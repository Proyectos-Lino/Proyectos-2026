package com.odontocare.finance.service;

import com.odontocare.shared.web.ApiException;
import java.util.Currency;

public final class FinanceMoney {
  private FinanceMoney() {}

  public static void requireCurrency(String code) {
    try {
      Currency.getInstance(code);
    } catch (IllegalArgumentException e) {
      throw ApiException.badRequest("Selecciona una moneda válida.");
    }
  }
}
