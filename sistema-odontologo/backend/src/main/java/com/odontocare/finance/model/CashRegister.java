package com.odontocare.finance.model;

import jakarta.persistence.*;

@Entity
@Table(name = "cash_register")
public class CashRegister {
  @Id private Short id;
}
