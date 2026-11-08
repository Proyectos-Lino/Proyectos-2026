"""Aplicación móvil integral con servicio de delivery y gestión en tiempo real."""
import asyncio
import csv
import io
import json
import os
import secrets
import sqlite3
import time
from collections import defaultdict, deque
from contextlib import asynccontextmanager
from datetime import datetime, timedelta, timezone, date
from pathlib import Path

from fastapi import FastAPI, Depends, HTTPException, Request, WebSocket, WebSocketDisconnect, UploadFile, File
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import Response
from fastapi.staticfiles import StaticFiles
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from PIL import Image, ImageOps, UnidentifiedImageError
from . import db, schemas as s
from .security import now, hash_password, verify_password, token_hash

def fail(message, code=400):
    raise HTTPException(code,message)

def safe_user(row):
    return {k:row[k] for k in ('id','name','email','phone','role','active','created_at')}

def authenticate(token):
    with db.transaction() as c:
        row=c.execute('SELECT u.* FROM sessions s JOIN users u ON u.id=s.user_id WHERE s.token=? AND s.expires_at>? AND u.active=1',(token_hash(token),now())).fetchone()
        if not row: fail('Tu sesión expiró. Vuelve a ingresar.',401)
        return safe_user(row)

bearer=HTTPBearer(auto_error=False)
def current(credentials: HTTPAuthorizationCredentials | None = Depends(bearer)):
    if credentials is None or credentials.scheme.lower()!='bearer': fail('Inicia sesión para continuar.',401)
    return authenticate(credentials.credentials)

def admin(user=Depends(current)):
    if user['role']!='admin': fail('Esta función requiere un administrador.',403)
    return user

def audit(c,user,action):
    c.execute('INSERT INTO audit(user_id,action,created_at) VALUES(?,?,?)',(user['id'],action,now()))

class Hub:
    def __init__(self): self.clients={}
    async def publish(self, kind, customer=None, driver=None):
        for ws,user in list(self.clients.items()):
            if customer is not None and user['role']!='admin' and user['id'] not in (customer,driver): continue
            try:
                await asyncio.wait_for(ws.send_json({'type':kind,'at':now()}),2)
            except Exception:
                self.clients.pop(ws,None)
hub=Hub()

@asynccontextmanager
async def lifespan(app):
    db.initialize(hash_password,now)
    yield
    for ws in list(hub.clients):
        await ws.close()
    hub.clients.clear()

app=FastAPI(title='Integral Delivery API',version='1.0.0',lifespan=lifespan)
origins=[x.strip() for x in os.getenv('CORS_ORIGINS','').split(',') if x.strip()]
if origins:
    app.add_middleware(CORSMiddleware,allow_origins=origins,allow_methods=['GET','POST','PUT','PATCH','DELETE'],allow_headers=['Authorization','Content-Type'])
(db.DATA_DIR/'uploads').mkdir(parents=True,exist_ok=True)
app.mount('/uploads',StaticFiles(directory=db.DATA_DIR/'uploads'),name='uploads')

@app.exception_handler(sqlite3.IntegrityError)
async def integrity_handler(request,exc):
    from fastapi.responses import JSONResponse
    return JSONResponse({'detail':'Ya existe un registro con estos datos o la referencia no es válida.'},status_code=409)

@app.middleware('http')
async def headers(request,call_next):
    # Las fotografías no se almacenan sin límite. Los tokens nunca se escriben en logs.
    length=request.headers.get('content-length')
    if length and int(length)>8*1024*1024:
        from fastapi.responses import JSONResponse
        return JSONResponse({'detail':'El archivo supera el límite de 8 MB.'},status_code=413)
    response=await call_next(request)
    response.headers['X-Content-Type-Options']='nosniff'
    response.headers['Cache-Control']='no-store' if request.url.path.startswith('/api') else 'public, max-age=3600'
    return response

@app.get('/health')
def health():
    with db.transaction() as c: c.execute('SELECT 1')
    return {'status':'ok','version':'1.0.0'}

@app.get('/api/public')
def public():
    with db.transaction() as c: config=db.settings(c)
    return {'business':config,'title':'Aplicación móvil integral con servicio de delivery y gestión en tiempo real'}

attempts=defaultdict(deque)
def throttle(request,email):
    key=(request.client.host if request.client else 'local',email.lower())
    queue=attempts[key]
    cutoff=time.monotonic()-300
    while queue and queue[0]<cutoff: queue.popleft()
    if len(queue)>=15: fail('Demasiados intentos. Espera cinco minutos.',429)
    queue.append(time.monotonic())
    if len(attempts)>5000:
        for k in list(attempts):
            if not attempts[k] or attempts[k][-1]<cutoff: attempts.pop(k,None)

def session(c,user):
    token=secrets.token_urlsafe(40)
    expiry=(datetime.now(timezone.utc)+timedelta(days=14)).isoformat()
    c.execute('DELETE FROM sessions WHERE expires_at<?',(now(),))
    c.execute('INSERT INTO sessions VALUES(?,?,?)',(token_hash(token),user['id'],expiry))
    return {'token':token,'user':safe_user(user)}

@app.post('/api/auth/login')
def login(data:s.Login,request:Request):
    throttle(request,data.email)
    with db.transaction(True) as c:
        u=c.execute('SELECT * FROM users WHERE email=?',(data.email.lower(),)).fetchone()
        if not u or not verify_password(data.password,u['password']) or not u['active']:
            fail('Correo o contraseña incorrectos, o cuenta desactivada.',401)
        return session(c,u)

@app.post('/api/auth/register',status_code=201)
async def register(data:s.Register,request:Request):
    throttle(request,'register')
    with db.transaction(True) as c:
        cur=c.execute('INSERT INTO users(name,email,phone,password,role,created_at) VALUES(?,?,?,?,?,?)',(data.name,data.email,data.phone,hash_password(data.password),'customer',now()))
        result=session(c,c.execute('SELECT * FROM users WHERE id=?',(cur.lastrowid,)).fetchone())
    await hub.publish('users')
    return result

@app.post('/api/auth/logout')
async def logout(request:Request,user=Depends(current)):
    with db.transaction(True) as c: c.execute('DELETE FROM sessions WHERE token=?',(token_hash(request.headers['authorization'][7:]),))
    return {'ok':True}

@app.get('/api/me')
def me(user=Depends(current)): return user

@app.patch('/api/me')
async def profile(data:s.Profile,user=Depends(current)):
    with db.transaction(True) as c:
        c.execute('UPDATE users SET name=?,phone=? WHERE id=?',(data.name,data.phone,user['id']))
    await hub.publish('users')
    return {'ok':True}

@app.post('/api/me/password')
async def password(data:s.Password,user=Depends(current)):
    with db.transaction(True) as c:
        old=c.execute('SELECT password FROM users WHERE id=?',(user['id'],)).fetchone()[0]
        if not verify_password(data.current,old): fail('La contraseña actual no es correcta.')
        c.execute('UPDATE users SET password=? WHERE id=?',(hash_password(data.new),user['id']))
        c.execute('DELETE FROM sessions WHERE user_id=?',(user['id'],))
        audit(c,user,'Cambio de contraseña')
    await hub.publish('session',customer=user['id'])
    return {'ok':True,'login_required':True}

@app.post('/api/addresses',status_code=201)
async def address(data:s.Address,user=Depends(current)):
    with db.transaction(True) as c:
        cur=c.execute('INSERT INTO addresses(user_id,label,street,district,reference) VALUES(?,?,?,?,?)',(user['id'],data.label,data.street,data.district,data.reference))
        ident=cur.lastrowid
    return {'id':ident}

@app.delete('/api/addresses/{ident}')
def delete_address(ident:int,user=Depends(current)):
    with db.transaction(True) as c:
        if not c.execute('DELETE FROM addresses WHERE id=? AND user_id=?',(ident,user['id'])).rowcount: fail('Dirección no encontrada.',404)
    return {'ok':True}

def order_view(c,row):
    data=dict(row)
    data['items']=[dict(x) for x in c.execute('SELECT product_id,name,quantity,price_cents FROM order_items WHERE order_id=?',(row['id'],))]
    data['events']=[dict(x) for x in c.execute('SELECT e.kind,e.message,e.created_at,u.name AS actor FROM order_events e LEFT JOIN users u ON u.id=e.user_id WHERE order_id=? ORDER BY e.id',(row['id'],))]
    customer=c.execute('SELECT id,name,phone,email FROM users WHERE id=?',(row['customer_id'],)).fetchone()
    data['customer']=dict(customer)
    driver=c.execute('SELECT id,name,phone FROM users WHERE id=?',(row['driver_id'],)).fetchone()
    data['driver']=dict(driver) if driver else None
    data.pop('idempotency_key',None)
    return data

def authorized_order(c,ident,user):
    o=c.execute('SELECT * FROM orders WHERE id=?',(ident,)).fetchone()
    if not o: fail('Pedido no encontrado.',404)
    if user['role']=='customer' and o['customer_id']!=user['id']: fail('No puedes acceder a este pedido.',403)
    if user['role']=='driver' and o['driver_id']!=user['id']: fail('Este pedido no está asignado a tu cuenta.',403)
    return o

def event(c,ident,user,kind,message):
    c.execute('INSERT INTO order_events(order_id,user_id,kind,message,created_at) VALUES(?,?,?,?,?)',(ident,user['id'],kind,message,now()))
    c.execute('UPDATE orders SET updated_at=? WHERE id=?',(now(),ident))

@app.get('/api/orders/{ident}')
def get_order(ident:int,user=Depends(current)):
    with db.transaction() as c: return order_view(c,authorized_order(c,ident,user))

@app.post('/api/orders',status_code=201)
async def create_order(data:s.OrderInput,user=Depends(current)):
    if user['role']!='customer': fail('Los pedidos se realizan desde una cuenta de cliente.',403)
    with db.transaction(True) as c:
        existing=c.execute('SELECT * FROM orders WHERE customer_id=? AND idempotency_key=?',(user['id'],data.idempotency_key)).fetchone()
        if existing: return order_view(c,existing)
        config=db.settings(c)
        if not config['is_open']: fail('El negocio está cerrado y no recibe pedidos por el momento.')
        address=c.execute('SELECT * FROM addresses WHERE id=? AND user_id=?',(data.address_id,user['id'])).fetchone()
        if not address: fail('Selecciona una dirección de tu cuenta.')
        zone=next((z for z in config['zones'] if z['id']==data.zone_id),None)
        if not zone: fail('La zona seleccionada ya no está disponible.')
        if data.payment_method not in config['payment_methods']: fail('Método de pago no disponible.')
        quantities=defaultdict(int)
        for item in data.items: quantities[item.product_id]+=item.quantity
        lines=[]
        subtotal=0
        for ident,qty in quantities.items():
            p=c.execute('SELECT p.* FROM products p JOIN categories cat ON cat.id=p.category_id WHERE p.id=? AND p.active=1 AND cat.active=1',(ident,)).fetchone()
            if not p: fail('Uno de los productos ya no está disponible.')
            if p['stock']<qty: fail(f"Stock insuficiente para {p['name']}. Disponible: {p['stock']}.")
            lines.append((p,qty))
            subtotal+=p['price_cents']*qty
        if subtotal<config['minimum_cents']: fail('El pedido no alcanza el importe mínimo configurado.')
        full_address=' · '.join(x for x in [address['street'],address['district'],address['reference']] if x)
        code='ID-'+datetime.now(timezone.utc).strftime('%y%m%d')+'-'+secrets.token_hex(3).upper()
        cur=c.execute('INSERT INTO orders(code,customer_id,payment_method,subtotal_cents,delivery_cents,total_cents,address,zone,notes,created_at,updated_at,idempotency_key) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)',
                      (code,user['id'],data.payment_method,subtotal,zone['fee_cents'],subtotal+zone['fee_cents'],full_address,zone['name'],data.notes,now(),now(),data.idempotency_key))
        ident=cur.lastrowid
        for p,qty in lines:
            c.execute('UPDATE products SET stock=stock-? WHERE id=?',(qty,p['id']))
            c.execute('INSERT INTO order_items(order_id,product_id,name,quantity,price_cents) VALUES(?,?,?,?,?)',(ident,p['id'],p['name'],qty,p['price_cents']))
        event(c,ident,user,'pending','Pedido recibido. Esperando confirmación del negocio.')
        result=order_view(c,c.execute('SELECT * FROM orders WHERE id=?',(ident,)).fetchone())
    await hub.publish('orders',customer=user['id'])
    await hub.publish('catalog')
    return result

TRANSITIONS={'pending':'confirmed','confirmed':'preparing','preparing':'ready','ready':'en_route','en_route':'delivered'}
LABELS={'pending':'Pendiente','confirmed':'Confirmado','preparing':'En preparación','ready':'Listo para recoger','en_route':'En camino','delivered':'Entregado','cancelled':'Cancelado'}

@app.post('/api/orders/{ident}/status')
async def status(ident:int,data:s.Status,user=Depends(current)):
    with db.transaction(True) as c:
        o=authorized_order(c,ident,user)
        if o['status'] in ('delivered','cancelled'): fail('El pedido ya finalizó.')
        if data.status=='cancelled':
            if user['role']=='driver': fail('El repartidor puede registrar una incidencia; la cancelación corresponde al negocio.',403)
            if user['role']=='customer' and o['status']!='pending': fail('Solo puedes cancelar pedidos pendientes de confirmación.')
            if o['payment_status']=='paid': fail('Registra primero la devolución y cambia el pago a pendiente antes de cancelar.')
            if len(data.message)<5: fail('Indica el motivo de cancelación (mínimo 5 caracteres).')
            for line in c.execute('SELECT product_id,quantity FROM order_items WHERE order_id=?',(ident,)).fetchall():
                c.execute('UPDATE products SET stock=stock+? WHERE id=?',(line['quantity'],line['product_id']))
        else:
            if user['role']=='customer': fail('Solo el negocio y el repartidor pueden avanzar el pedido.',403)
            if user['role']=='driver' and data.status not in ('en_route','delivered'): fail('Esta transición corresponde al negocio.',403)
            if TRANSITIONS.get(o['status'])!=data.status: fail('El estado cambió. Actualiza y sigue el orden del proceso.')
            if data.status in ('en_route','delivered') and not o['driver_id']: fail('Asigna un repartidor antes de iniciar la entrega.')
        c.execute('UPDATE orders SET status=? WHERE id=?',(data.status,ident))
        event(c,ident,user,data.status,LABELS[data.status]+('. '+data.message if data.message else ''))
        audit(c,user,f'Pedido {o["code"]}: {data.status}')
    await hub.publish('orders',o['customer_id'],o['driver_id'])
    if data.status=='cancelled': await hub.publish('catalog')
    return {'ok':True}

@app.post('/api/orders/{ident}/assign')
async def assign(ident:int,data:s.Assignment,user=Depends(admin)):
    with db.transaction(True) as c:
        o=authorized_order(c,ident,user)
        if o['status'] in ('en_route','delivered','cancelled'): fail('No se puede reasignar un pedido en camino o finalizado.')
        driver=c.execute("SELECT * FROM users WHERE id=? AND role='driver' AND active=1",(data.driver_id,)).fetchone()
        if not driver: fail('El repartidor no está disponible.')
        c.execute('UPDATE orders SET driver_id=? WHERE id=?',(data.driver_id,ident))
        event(c,ident,user,'assigned',f"Repartidor asignado: {driver['name']}.")
    await hub.publish('orders',o['customer_id'],data.driver_id)
    if o['driver_id'] and o['driver_id']!=data.driver_id: await hub.publish('orders',o['customer_id'],o['driver_id'])
    return {'ok':True}

@app.post('/api/orders/{ident}/payment')
async def payment(ident:int,data:s.Payment,user=Depends(current)):
    with db.transaction(True) as c:
        o=authorized_order(c,ident,user)
        if user['role']=='customer': fail('El negocio confirma los pagos.',403)
        if user['role']=='driver' and (o['payment_method']!='cash' or data.status!='paid' or o['status'] not in ('en_route','delivered')):
            fail('El repartidor solo puede registrar el cobro en efectivo durante la entrega.',403)
        if o['status']=='cancelled': fail('El pedido está cancelado.')
        c.execute('UPDATE orders SET payment_status=? WHERE id=?',(data.status,ident))
        event(c,ident,user,'payment','Pago registrado: '+('pagado' if data.status=='paid' else 'pendiente / devolución registrada')+'.')
        audit(c,user,f'Pago {o["code"]}: {data.status}')
    await hub.publish('orders',o['customer_id'],o['driver_id'])
    return {'ok':True}

@app.post('/api/orders/{ident}/issue')
async def issue(ident:int,data:s.Message,user=Depends(current)):
    with db.transaction(True) as c:
        o=authorized_order(c,ident,user)
        if o['status'] in ('delivered','cancelled'): fail('El pedido ya finalizó.')
        event(c,ident,user,'issue','Incidencia: '+data.message)
    await hub.publish('orders',o['customer_id'],o['driver_id'])
    return {'ok':True}

@app.post('/api/admin/categories',status_code=201)
async def add_category(data:s.Category,user=Depends(admin)):
    with db.transaction(True) as c:
        cur=c.execute('INSERT INTO categories(name,icon,active) VALUES(?,?,?)',(data.name,data.icon,data.active))
        ident=cur.lastrowid
        audit(c,user,'Categoría creada: '+data.name)
    await hub.publish('catalog')
    return {'id':ident}

@app.put('/api/admin/categories/{ident}')
async def update_category(ident:int,data:s.Category,user=Depends(admin)):
    with db.transaction(True) as c:
        if not c.execute('UPDATE categories SET name=?,icon=?,active=? WHERE id=?',(data.name,data.icon,data.active,ident)).rowcount: fail('Categoría no encontrada.',404)
        audit(c,user,'Categoría editada: '+data.name)
    await hub.publish('catalog')
    return {'ok':True}

def validate_product(c,data):
    if not c.execute('SELECT 1 FROM categories WHERE id=?',(data.category_id,)).fetchone(): fail('Categoría no encontrada.')
    if data.image_url and not data.image_url.startswith('/uploads/'): fail('Carga la imagen desde la aplicación.')

@app.post('/api/admin/products',status_code=201)
async def add_product(data:s.Product,user=Depends(admin)):
    with db.transaction(True) as c:
        validate_product(c,data)
        cur=c.execute('INSERT INTO products(name,category_id,description,price_cents,stock,image_url,active) VALUES(?,?,?,?,?,?,?)',tuple(data.model_dump().values()))
        ident=cur.lastrowid
        audit(c,user,'Producto creado: '+data.name)
    await hub.publish('catalog')
    return {'id':ident}

@app.put('/api/admin/products/{ident}')
async def update_product(ident:int,data:s.Product,user=Depends(admin)):
    with db.transaction(True) as c:
        validate_product(c,data)
        if not c.execute('UPDATE products SET name=?,category_id=?,description=?,price_cents=?,stock=?,image_url=?,active=? WHERE id=?',(*data.model_dump().values(),ident)).rowcount: fail('Producto no encontrado.',404)
        audit(c,user,'Producto editado: '+data.name)
    await hub.publish('catalog')
    return {'ok':True}

@app.post('/api/admin/users',status_code=201)
async def add_user(data:s.UserInput,user=Depends(admin)):
    with db.transaction(True) as c:
        cur=c.execute('INSERT INTO users(name,email,phone,password,role,active,created_at) VALUES(?,?,?,?,?,?,?)',(data.name,data.email,data.phone,hash_password(data.password),data.role,data.active,now()))
        ident=cur.lastrowid
        audit(c,user,'Usuario creado: '+data.email)
    await hub.publish('users')
    return {'id':ident}

@app.put('/api/admin/users/{ident}')
async def update_user(ident:int,data:s.UserUpdate,user=Depends(admin)):
    if data.password and len(data.password)<8: fail('La contraseña requiere al menos 8 caracteres.')
    with db.transaction(True) as c:
        old=c.execute('SELECT * FROM users WHERE id=?',(ident,)).fetchone()
        if not old: fail('Usuario no encontrado.',404)
        if ident==user['id'] and (not data.active or data.role!='admin'): fail('No puedes quitarte tu propio acceso de administrador.')
        if old['role']=='driver' and (data.role!='driver' or not data.active):
            if c.execute("SELECT 1 FROM orders WHERE driver_id=? AND status NOT IN ('delivered','cancelled')",(ident,)).fetchone(): fail('Reasigna o finaliza los pedidos activos de este repartidor antes de desactivarlo o cambiar su rol.')
        c.execute('UPDATE users SET name=?,phone=?,role=?,active=? WHERE id=?',(data.name,data.phone,data.role,data.active,ident))
        if data.password: c.execute('UPDATE users SET password=? WHERE id=?',(hash_password(data.password),ident))
        if data.password or old['role']!=data.role or not data.active: c.execute('DELETE FROM sessions WHERE user_id=?',(ident,))
        audit(c,user,'Usuario editado: '+old['email'])
    await hub.publish('users')
    return {'ok':True}

@app.put('/api/admin/business')
async def update_business(data:s.Business,user=Depends(admin)):
    if data.logo_url and not data.logo_url.startswith('/uploads/'): fail('Carga el logo desde la aplicación.')
    with db.transaction(True) as c:
        config=db.settings(c)
        if config['currency']!=data.currency and c.execute('SELECT 1 FROM orders LIMIT 1').fetchone(): fail('La moneda no se puede cambiar después de registrar pedidos.')
        c.execute('UPDATE settings SET value=? WHERE id=1',(json.dumps(data.model_dump()),))
        audit(c,user,'Configuración del negocio actualizada')
    await hub.publish('business')
    return {'ok':True}

@app.post('/api/admin/uploads')
async def upload(file:UploadFile=File(...),user=Depends(admin)):
    content=await file.read(8*1024*1024+1)
    if len(content)>8*1024*1024: fail('La imagen supera los 8 MB.',413)
    try:
        with Image.open(io.BytesIO(content)) as img:
            if img.width*img.height>25000000: fail('La imagen tiene demasiada resolución.')
            img=ImageOps.exif_transpose(img)
            if 'A' in img.getbands():
                rgba=img.convert('RGBA')
                img=Image.new('RGB',rgba.size,'white')
                img.paste(rgba,mask=rgba.getchannel('A'))
            else: img=img.convert('RGB')
            img.thumbnail((1200,1200))
            filename=secrets.token_hex(16)+'.jpg'
            img.save(db.DATA_DIR/'uploads'/filename,'JPEG',quality=85)
    except (UnidentifiedImageError,OSError,Image.DecompressionBombError):
        fail('Selecciona una imagen JPG, PNG o WebP válida.')
    return {'url':'/uploads/'+filename}

def reports(c,start='',end=''):
    where=' WHERE 1=1'
    args=[]
    for value,op in [(start,'>='),(end,'<=')]:
        if value:
            try: date.fromisoformat(value)
            except ValueError: fail('Fecha inválida. Usa AAAA-MM-DD.')
            where+=' AND substr(created_at,1,10)'+op+'?'
            args.append(value)
    if start and end and start>end: fail('La fecha inicial no puede ser posterior a la fecha final.')
    rows=[dict(r) for r in c.execute('SELECT * FROM orders'+where+' ORDER BY id DESC',args)]
    valid=[r for r in rows if r['status']!='cancelled']
    return {'count':len(rows),'sales_cents':sum(r['total_cents'] for r in valid),
            'collected_cents':sum(r['total_cents'] for r in valid if r['payment_status']=='paid'),
            'pending_cents':sum(r['total_cents'] for r in valid if r['payment_status']=='pending'),
            'delivered':sum(r['status']=='delivered' for r in rows),
            'cancelled':sum(r['status']=='cancelled' for r in rows),
            'active':sum(r['status'] not in ('delivered','cancelled') for r in rows),
            'by_status':{k:sum(r['status']==k for r in rows) for k in LABELS},
            'daily':[{'day':day,'total_cents':sum(r['total_cents'] for r in valid if r['created_at'][:10]==day)} for day in sorted({r['created_at'][:10] for r in rows})[-14:]],
            'low_stock':[dict(r) for r in c.execute('SELECT id,name,stock FROM products WHERE active=1 AND stock<=5 ORDER BY stock,name')],
            'top_products':[dict(r) for r in c.execute("SELECT i.name,SUM(i.quantity) AS quantity,SUM(i.quantity*i.price_cents) AS total_cents FROM order_items i JOIN orders o ON o.id=i.order_id"+where.replace('created_at','o.created_at')+" AND o.status!='cancelled' GROUP BY i.name ORDER BY quantity DESC LIMIT 5",args)]}

@app.get('/api/admin/reports')
def get_reports(start:str='',end:str='',user=Depends(admin)):
    with db.transaction() as c: return reports(c,start,end)

@app.get('/api/admin/reports.csv')
def export(start:str='',end:str='',user=Depends(admin)):
    with db.transaction() as c:
        reports(c,start,end) # valida fechas
        rows=c.execute('SELECT o.*,u.name AS customer_name FROM orders o JOIN users u ON u.id=o.customer_id WHERE (?="" OR substr(o.created_at,1,10)>=?) AND (?="" OR substr(o.created_at,1,10)<=?) ORDER BY o.id',(start,start,end,end)).fetchall()
    buf=io.StringIO(newline='')
    writer=csv.writer(buf)
    writer.writerow(['Pedido','Fecha UTC','Cliente','Estado','Pago','Productos','Delivery','Total'])
    for r in rows:
        name=r['customer_name']
        if name.lstrip().startswith(('=','+','-','@')) or name.startswith(('\t','\r')): name="'"+name
        writer.writerow([r['code'],r['created_at'],name,LABELS[r['status']],r['payment_status'],f"{r['subtotal_cents']/100:.2f}",f"{r['delivery_cents']/100:.2f}",f"{r['total_cents']/100:.2f}"])
    return Response('\ufeff'+buf.getvalue(),media_type='text/csv; charset=utf-8',headers={'Content-Disposition':'attachment; filename="pedidos.csv"'})

@app.get('/api/sync')
def sync(user=Depends(current)):
    with db.transaction() as c:
        is_admin=user['role']=='admin'
        cats=[dict(r) for r in c.execute('SELECT * FROM categories'+('' if is_admin else ' WHERE active=1')+' ORDER BY name')]
        products=[dict(r) for r in c.execute('SELECT p.*,cat.name AS category_name,cat.icon AS category_icon FROM products p JOIN categories cat ON cat.id=p.category_id'+('' if is_admin else ' WHERE p.active=1 AND cat.active=1')+' ORDER BY p.id DESC')]
        clause='' if is_admin else (' WHERE customer_id=?' if user['role']=='customer' else ' WHERE driver_id=?')
        orders=[order_view(c,r) for r in c.execute('SELECT * FROM orders'+clause+' ORDER BY id DESC LIMIT 500',() if is_admin else (user['id'],)).fetchall()]
        result={'user':user,'business':db.settings(c),'categories':cats,'products':products,'orders':orders,
                'addresses':[dict(r) for r in c.execute('SELECT * FROM addresses WHERE user_id=? ORDER BY id',(user['id'],))]}
        if is_admin:
            result['users']=[safe_user(r) for r in c.execute('SELECT * FROM users ORDER BY id')]
            result['reports']=reports(c)
            result['audit']=[dict(r) for r in c.execute('SELECT a.action,a.created_at,u.name AS actor FROM audit a LEFT JOIN users u ON u.id=a.user_id ORDER BY a.id DESC LIMIT 100')]
        return result

@app.websocket('/api/ws')
async def websocket(ws:WebSocket):
    header=ws.headers.get('authorization','')
    if not header.startswith('Bearer '):
        await ws.close(code=4401)
        return
    token=header[7:]
    try: user=authenticate(token)
    except HTTPException:
        await ws.close(code=4401)
        return
    await ws.accept()
    hub.clients[ws]=user
    try:
        await ws.send_json({'type':'ready','at':now()})
        while True:
            try: message=await asyncio.wait_for(ws.receive_text(),35)
            except asyncio.TimeoutError: message='ping'
            try: authenticate(token)
            except HTTPException:
                await ws.close(code=4401)
                break
            if message=='ping': await ws.send_json({'type':'pong','at':now()})
    except (WebSocketDisconnect,RuntimeError): pass
    finally: hub.clients.pop(ws,None)
