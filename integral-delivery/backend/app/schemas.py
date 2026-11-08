from typing import Literal
from pydantic import BaseModel, Field, field_validator

class Input(BaseModel):
    model_config = {"extra": "forbid", "str_strip_whitespace": True}

class Login(Input):
    email: str = Field(min_length=3,max_length=180)
    password: str = Field(min_length=1,max_length=200)

class Register(Login):
    name: str = Field(min_length=2,max_length=100)
    phone: str = Field(default='',max_length=30)
    @field_validator('password')
    @classmethod
    def strong(cls,v):
        if len(v)<8: raise ValueError('La contraseña debe tener al menos 8 caracteres.')
        return v
    @field_validator('email')
    @classmethod
    def email_format(cls,v):
        if '@' not in v or '.' not in v.split('@')[-1]: raise ValueError('Correo electrónico inválido.')
        return v.lower()

class UserInput(Register):
    role: Literal['admin','customer','driver'] = 'customer'
    active: bool = True

class UserUpdate(Input):
    name: str = Field(min_length=2,max_length=100)
    phone: str = Field(default='',max_length=30)
    role: Literal['admin','customer','driver']
    active: bool = True
    password: str = Field(default='',max_length=200)

class Profile(Input):
    name: str = Field(min_length=2,max_length=100)
    phone: str = Field(default='',max_length=30)

class Password(Input):
    current: str = Field(min_length=1,max_length=200)
    new: str = Field(min_length=8,max_length=200)

class Address(Input):
    label: str = Field(min_length=1,max_length=50)
    street: str = Field(min_length=5,max_length=250)
    district: str = Field(default='',max_length=100)
    reference: str = Field(default='',max_length=250)

class Category(Input):
    name: str = Field(min_length=2,max_length=70)
    icon: str = Field(default='shop',max_length=30)
    active: bool = True

class Product(Input):
    name: str = Field(min_length=2,max_length=120)
    category_id: int = Field(gt=0)
    description: str = Field(default='',max_length=1500)
    price_cents: int = Field(ge=0,le=100000000)
    stock: int = Field(ge=0,le=1000000)
    image_url: str = Field(default='',max_length=500)
    active: bool = True

class Item(Input):
    product_id: int = Field(gt=0)
    quantity: int = Field(ge=1,le=999)

class OrderInput(Input):
    items: list[Item] = Field(min_length=1,max_length=100)
    address_id: int = Field(gt=0)
    zone_id: str = Field(max_length=80)
    payment_method: Literal['cash','transfer']
    notes: str = Field(default='',max_length=500)
    idempotency_key: str = Field(min_length=8,max_length=100)

class Status(Input):
    status: Literal['confirmed','preparing','ready','en_route','delivered','cancelled']
    message: str = Field(default='',max_length=500)

class Assignment(Input):
    driver_id: int = Field(gt=0)

class Payment(Input):
    status: Literal['pending','paid']

class Message(Input):
    message: str = Field(min_length=5,max_length=1000)

class Zone(Input):
    id: str = Field(min_length=1,max_length=80)
    name: str = Field(min_length=2,max_length=100)
    fee_cents: int = Field(ge=0,le=10000000)

class Business(Input):
    name: str = Field(min_length=2,max_length=100)
    description: str = Field(default='',max_length=500)
    phone: str = Field(default='',max_length=30)
    email: str = Field(default='',max_length=180)
    address: str = Field(default='',max_length=300)
    currency: str = Field(default='PEN',min_length=3,max_length=3)
    symbol: str = Field(default='S/',min_length=1,max_length=6)
    color: str = Field(default='#EC6838',pattern=r'^#[0-9A-Fa-f]{6}$')
    logo_url: str = Field(default='',max_length=500)
    hours: str = Field(default='',max_length=300)
    is_open: bool = True
    minimum_cents: int = Field(default=0,ge=0,le=100000000)
    zones: list[Zone] = Field(min_length=1,max_length=30)
    payment_methods: list[Literal['cash','transfer']] = Field(min_length=1,max_length=2)
    bank_details: str = Field(default='',max_length=1000)
    @field_validator('zones')
    @classmethod
    def unique_zones(cls,v):
        if len({z.id for z in v})!=len(v): raise ValueError('Los códigos de zona deben ser únicos.')
        return v
