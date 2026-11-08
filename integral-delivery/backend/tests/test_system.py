"""Pruebas de reglas de negocio y aislamiento entre los tres perfiles."""
import io
import os
import tempfile
from concurrent.futures import ThreadPoolExecutor

os.environ['DATA_DIR']=tempfile.mkdtemp(prefix='integral-tests-')
os.environ['SEED_DEMO']='true'
os.environ['APP_ENV']='testing'
from fastapi.testclient import TestClient
from PIL import Image
from app.main import app, attempts
from app import db
import pytest

@pytest.fixture()
def api(tmp_path,monkeypatch):
    monkeypatch.setattr(db,'DATA_DIR',tmp_path)
    (tmp_path/'uploads').mkdir()
    for route in app.routes:
        if getattr(route,'path',None)=='/uploads':
            monkeypatch.setattr(route.app,'directory',str(tmp_path/'uploads'))
            monkeypatch.setattr(route.app,'all_directories',[str(tmp_path/'uploads')])
    attempts.clear()
    with TestClient(app) as client: yield client

def auth(api,role):
    email,pw={'admin':('admin@integral.local','Admin2026!'),'customer':('cliente@demo.local','Cliente2026!'),'driver':('repartidor@demo.local','Reparto2026!')}[role]
    r=api.post('/api/auth/login',json={'email':email,'password':pw})
    assert r.status_code==200,r.text
    return {'Authorization':'Bearer '+r.json()['token']}

def create(api,h,key='test-key-123',quantity=1,payment='cash'):
    return api.post('/api/orders',headers=h,json={'items':[{'product_id':1,'quantity':quantity}],'address_id':1,'zone_id':'centro','payment_method':payment,'idempotency_key':key})

def test_complete_delivery_and_finances(api):
    customer,admin,driver=[auth(api,r) for r in ('customer','admin','driver')]
    result=create(api,customer,quantity=2)
    assert result.status_code==201,result.text
    order=result.json(); ident=order['id']
    assert order['total_cents']==5000
    assert api.get('/api/sync',headers=customer).json()['products'][-1]['stock']==23
    for state in ('confirmed','preparing','ready'):
        assert api.post(f'/api/orders/{ident}/status',headers=admin,json={'status':state}).status_code==200
    assert api.post(f'/api/orders/{ident}/status',headers=admin,json={'status':'en_route'}).status_code==400
    assert api.post(f'/api/orders/{ident}/assign',headers=admin,json={'driver_id':3}).status_code==200
    assert len(api.get('/api/sync',headers=driver).json()['orders'])==1
    for state in ('en_route','delivered'):
        assert api.post(f'/api/orders/{ident}/status',headers=driver,json={'status':state}).status_code==200
    assert api.post(f'/api/orders/{ident}/payment',headers=driver,json={'status':'paid'}).status_code==200
    result=api.get('/api/orders/'+str(ident),headers=customer).json()
    assert result['payment_status']=='paid' and result['status']=='delivered'
    assert len(result['events'])==8
    report=api.get('/api/admin/reports',headers=admin).json()
    assert report['collected_cents']==5000 and report['delivered']==1
    csv=api.get('/api/admin/reports.csv',headers=admin)
    assert csv.status_code==200 and order['code'] in csv.text

def test_idempotency_stock_and_cancellation(api):
    h=auth(api,'customer')
    order=create(api,h).json()
    assert create(api,h).json()['id']==order['id']
    assert len(api.get('/api/sync',headers=h).json()['orders'])==1
    assert create(api,h,key='too-many',quantity=100).status_code==400
    assert api.post(f"/api/orders/{order['id']}/status",headers=h,json={'status':'cancelled','message':'Pedido duplicado'}).status_code==200
    assert api.post(f"/api/orders/{order['id']}/status",headers=h,json={'status':'cancelled','message':'Pedido duplicado'}).status_code==400
    stock=next(p['stock'] for p in api.get('/api/sync',headers=h).json()['products'] if p['id']==1)
    assert stock==25
    assert api.get('/api/sync',headers=auth(api,'admin')).json()['reports']['sales_cents']==0

def test_permissions_and_foreign_orders(api):
    h=auth(api,'customer'); a=auth(api,'admin'); d=auth(api,'driver')
    ident=create(api,h).json()['id']
    new=api.post('/api/auth/register',json={'name':'Otro cliente','email':'otro@example.com','password':'Prueba123!'}).json()
    other={'Authorization':'Bearer '+new['token']}
    assert api.get(f'/api/orders/{ident}',headers=other).status_code==403
    assert api.get(f'/api/orders/{ident}',headers=d).status_code==403
    assert api.post(f'/api/orders/{ident}/status',headers=h,json={'status':'confirmed'}).status_code==403
    assert api.get('/api/admin/reports',headers=h).status_code==403
    assert api.put('/api/admin/users/1',headers=a,json={'name':'Admin','role':'customer','active':True}).status_code==400
    assert api.post('/api/auth/register',json={'name':'Ataque','email':'bad@example.com','password':'Prueba123!','role':'admin'}).status_code==422
    assert api.get('/api/sync').status_code==401
    assert all('password' not in u for u in api.get('/api/sync',headers=a).json()['users'])

def test_settings_catalog_upload_and_user_management(api):
    a=auth(api,'admin'); h=auth(api,'customer')
    config=api.get('/api/public').json()['business']
    config.update(name='Ferretería Integral',is_open=False)
    assert api.put('/api/admin/business',headers=a,json=config).status_code==200
    assert create(api,h).status_code==400
    config['is_open']=True
    assert api.put('/api/admin/business',headers=a,json=config).status_code==200
    cat=api.post('/api/admin/categories',headers=a,json={'name':'Herramientas','icon':'build'}).json()['id']
    b=io.BytesIO(); Image.new('RGB',(40,40),'orange').save(b,format='PNG')
    image=api.post('/api/admin/uploads',headers=a,files={'file':('prueba.png',b.getvalue(),'image/png')})
    assert image.status_code==200,image.text
    downloaded=api.get(image.json()['url'])
    assert downloaded.status_code==200 and downloaded.headers['content-type']=='image/jpeg'
    p={'name':'Martillo','category_id':cat,'price_cents':1900,'stock':3,'image_url':image.json()['url']}
    ident=api.post('/api/admin/products',headers=a,json=p).json()['id']
    p['stock']=4
    assert api.put(f'/api/admin/products/{ident}',headers=a,json=p).status_code==200
    p['stock']=-1
    assert api.put(f'/api/admin/products/{ident}',headers=a,json=p).status_code==422
    assert api.post('/api/admin/uploads',headers=a,files={'file':('bad.png',b'no image','image/png')}).status_code==400
    u=api.post('/api/admin/users',headers=a,json={'name':'Segundo reparto','email':'driver@example.com','password':'Prueba123!','role':'driver'}).json()['id']
    assert api.put(f'/api/admin/users/{u}',headers=a,json={'name':'Segundo reparto','role':'driver','active':False}).status_code==200
    assert api.get('/api/admin/reports?start=invalid',headers=a).status_code==400

def test_websocket_scoped_events_and_logout(api):
    h=auth(api,'customer'); a=auth(api,'admin')
    with api.websocket_connect('/api/ws',headers=a) as ws:
        assert ws.receive_json()['type']=='ready'
        create(api,h)
        assert ws.receive_json()['type']=='orders'
        assert ws.receive_json()['type']=='catalog'
        ws.send_text('ping')
        assert ws.receive_json()['type']=='pong'
    assert api.post('/api/auth/logout',headers=h).status_code==200
    assert api.get('/api/sync',headers=h).status_code==401

def test_concurrent_stock_and_same_key(api):
    h=auth(api,'customer')
    with ThreadPoolExecutor(max_workers=2) as pool:
        results=list(pool.map(lambda i:create(api,h,key=f'concurrent-{i}',quantity=20),range(2)))
    assert sorted(r.status_code for r in results)==[201,400]
    with ThreadPoolExecutor(max_workers=2) as pool:
        results=list(pool.map(lambda _:create(api,h,key='same-concurrent'),range(2)))
    assert results[0].json()['id']==results[1].json()['id']

def test_transfer_paid_cancellation_and_session_revocation(api):
    h=auth(api,'customer'); a=auth(api,'admin'); d=auth(api,'driver')
    ident=create(api,h,payment='transfer').json()['id']
    assert api.post(f'/api/orders/{ident}/assign',headers=a,json={'driver_id':3}).status_code==200
    assert api.post(f'/api/orders/{ident}/payment',headers=d,json={'status':'paid'}).status_code==403
    assert api.post(f'/api/orders/{ident}/payment',headers=a,json={'status':'paid'}).status_code==200
    assert api.post(f'/api/orders/{ident}/status',headers=a,json={'status':'cancelled','message':'Prueba devolución'}).status_code==400
    assert api.post(f'/api/orders/{ident}/payment',headers=a,json={'status':'pending'}).status_code==200
    assert api.post(f'/api/orders/{ident}/status',headers=a,json={'status':'cancelled','message':'Prueba devolución'}).status_code==200
    assert api.post('/api/me/password',headers=h,json={'current':'Cliente2026!','new':'Nueva2026!'}).status_code==200
    assert api.get('/api/sync',headers=h).status_code==401

def test_visibility_minimum_address_and_business_rules(api):
    h=auth(api,'customer'); a=auth(api,'admin')
    config=api.get('/api/public').json()['business']
    config['minimum_cents']=10000
    assert api.put('/api/admin/business',headers=a,json=config).status_code==200
    assert create(api,h).status_code==400
    config['minimum_cents']=0
    assert api.put('/api/admin/business',headers=a,json=config).status_code==200
    assert api.put('/api/admin/categories/1',headers=a,json={'name':'Despensa','active':False}).status_code==200
    assert create(api,h).status_code==400
    assert not any(p['category_id']==1 for p in api.get('/api/sync',headers=h).json()['products'])
    assert api.put('/api/admin/categories/1',headers=a,json={'name':'Despensa','active':True}).status_code==200
    ident=create(api,h).json()['id']
    assert api.post(f'/api/orders/{ident}/status',headers=a,json={'status':'confirmed'}).status_code==200
    assert api.post(f'/api/orders/{ident}/status',headers=h,json={'status':'cancelled','message':'Ya no lo necesito'}).status_code==400
    config['currency']='USD'
    assert api.put('/api/admin/business',headers=a,json=config).status_code==400
    assert api.delete('/api/addresses/1',headers=a).status_code==404
    assert api.post(f'/api/orders/{ident}/assign',headers=a,json={'driver_id':2}).status_code==400

def test_revoked_users_and_scoped_driver_history(api):
    h=auth(api,'customer'); a=auth(api,'admin'); d=auth(api,'driver')
    ident=create(api,h).json()['id']
    assert api.post(f'/api/orders/{ident}/assign',headers=a,json={'driver_id':3}).status_code==200
    payload={'name':'Diego','role':'driver','active':False}
    assert api.put('/api/admin/users/3',headers=a,json=payload).status_code==400
    assert api.post(f'/api/orders/{ident}/status',headers=a,json={'status':'cancelled','message':'Prueba de desactivación'}).status_code==200
    assert api.put('/api/admin/users/3',headers=a,json=payload).status_code==200
    assert api.get('/api/sync',headers=d).status_code==401
    assert api.post('/api/auth/login',json={'email':'repartidor@demo.local','password':'Reparto2026!'}).status_code==401

def test_restart_keeps_business_and_orders(api):
    from app.security import hash_password,now
    h=auth(api,'customer');a=auth(api,'admin')
    ident=create(api,h).json()['id']
    config=api.get('/api/public').json()['business'];config['name']='Mi Tienda Persistente'
    api.put('/api/admin/business',headers=a,json=config)
    db.initialize(hash_password,now)
    assert api.get('/api/public').json()['business']['name']=='Mi Tienda Persistente'
    assert api.get('/api/orders/'+str(ident),headers=h).status_code==200
    assert len(api.get('/api/sync',headers=a).json()['users'])==3

def test_production_initialization_needs_password_and_skips_demo(api,tmp_path,monkeypatch):
    from app.security import hash_password,now
    monkeypatch.setattr(db,'DATA_DIR',tmp_path/'production')
    monkeypatch.setenv('APP_ENV','production')
    monkeypatch.setenv('SEED_DEMO','false')
    monkeypatch.delenv('ADMIN_PASSWORD',raising=False)
    with pytest.raises(RuntimeError): db.initialize(hash_password,now)
    monkeypatch.setenv('ADMIN_PASSWORD','AdministradorUnico2026!')
    monkeypatch.setenv('ADMIN_EMAIL','admin@empresa.example')
    db.initialize(hash_password,now)
    with db.transaction() as c:
        assert c.execute('SELECT COUNT(*) FROM users').fetchone()[0]==1
        assert c.execute('SELECT COUNT(*) FROM products').fetchone()[0]==0
        assert c.execute('SELECT email FROM users').fetchone()[0]=='admin@empresa.example'
