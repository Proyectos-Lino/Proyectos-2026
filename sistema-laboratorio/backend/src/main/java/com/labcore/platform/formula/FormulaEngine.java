package com.labcore.platform.formula;

import com.labcore.platform.catalog.CalculationScope;
import com.labcore.platform.common.exception.BusinessRuleViolationException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Component;

@Component
public class FormulaEngine {
  private static final int INTERNAL_SCALE = 12;

  public record Definition(
      String fieldKey, String formula, CalculationScope scope, Integer decimalPrecision) {}

  public record Calculation(
      String fieldKey,
      int repetitionIndex,
      String formula,
      Map<String, List<BigDecimal>> inputs,
      BigDecimal value) {}

  public void validate(List<Definition> definitions, Set<String> availableFieldKeys) {
    Map<String, Expression> expressions = parseDefinitions(definitions);
    Set<String> available = normalizeKeys(availableFieldKeys);
    for (Map.Entry<String, Expression> entry : expressions.entrySet()) {
      for (String reference : entry.getValue().references()) {
        if (!available.contains(reference)) {
          throw rule(
              "La fórmula de «%s» referencia el campo inexistente «%s»."
                  .formatted(entry.getKey(), reference));
        }
      }
    }
    detectCycles(expressions);
  }

  public Set<String> references(String formula) {
    if (formula == null || formula.isBlank()) {
      throw rule("El campo calculado requiere una fórmula.");
    }
    return Set.copyOf(new Parser(formula).parse().references());
  }

  public List<Calculation> calculate(
      List<Definition> definitions,
      Set<String> availableFieldKeys,
      Map<String, Map<Integer, BigDecimal>> capturedValues) {
    validate(definitions, availableFieldKeys);
    Map<String, Definition> definitionsByKey = new LinkedHashMap<>();
    Map<String, Expression> expressions = new HashMap<>();
    for (Definition definition : definitions) {
      String key = normalizeKey(definition.fieldKey());
      definitionsByKey.put(key, definition);
      expressions.put(key, new Parser(definition.formula()).parse());
    }
    Map<String, Map<Integer, BigDecimal>> normalizedCaptured = new HashMap<>();
    capturedValues.forEach(
        (key, values) -> normalizedCaptured.put(normalizeKey(key), new HashMap<>(values)));
    TreeSet<Integer> repetitions = new TreeSet<>();
    normalizedCaptured.values().forEach(values -> repetitions.addAll(values.keySet()));
    if (repetitions.isEmpty()) {
      repetitions.add(0);
    }
    Evaluation evaluation =
        new Evaluation(definitionsByKey, expressions, normalizedCaptured, List.copyOf(repetitions));
    List<Calculation> calculations = new ArrayList<>();
    for (Map.Entry<String, Definition> entry : definitionsByKey.entrySet()) {
      List<Integer> targets =
          entry.getValue().scope() == CalculationScope.AGGREGATE
              ? List.of(0)
              : List.copyOf(repetitions);
      for (int repetition : targets) {
        BigDecimal value = evaluation.resolve(entry.getKey(), repetition);
        int precision =
            entry.getValue().decimalPrecision() == null ? 6 : entry.getValue().decimalPrecision();
        calculations.add(
            new Calculation(
                entry.getKey(),
                repetition,
                entry.getValue().formula(),
                evaluation.inputs(
                    expressions.get(entry.getKey()).references(),
                    repetition,
                    entry.getValue().scope()),
                value.setScale(precision, RoundingMode.HALF_UP)));
      }
    }
    return calculations;
  }

  private Map<String, Expression> parseDefinitions(List<Definition> definitions) {
    Map<String, Expression> expressions = new LinkedHashMap<>();
    for (Definition definition : definitions) {
      String key = normalizeKey(definition.fieldKey());
      if (definition.formula() == null || definition.formula().isBlank()) {
        throw rule("El campo calculado «%s» requiere una fórmula.".formatted(key));
      }
      if (expressions.put(key, new Parser(definition.formula()).parse()) != null) {
        throw rule("Existe más de una fórmula para el campo «%s».".formatted(key));
      }
    }
    return expressions;
  }

  private void detectCycles(Map<String, Expression> expressions) {
    Set<String> visited = new HashSet<>();
    Set<String> path = new HashSet<>();
    for (String key : expressions.keySet()) {
      visit(key, expressions, visited, path);
    }
  }

  private void visit(
      String key, Map<String, Expression> expressions, Set<String> visited, Set<String> path) {
    if (visited.contains(key)) {
      return;
    }
    if (!path.add(key)) {
      throw rule("Las fórmulas contienen una dependencia circular en «%s».".formatted(key));
    }
    Expression expression = expressions.get(key);
    if (expression != null) {
      expression.references().stream()
          .filter(expressions::containsKey)
          .forEach(reference -> visit(reference, expressions, visited, path));
    }
    path.remove(key);
    visited.add(key);
  }

  private Set<String> normalizeKeys(Set<String> keys) {
    Set<String> normalized = new HashSet<>();
    keys.forEach(key -> normalized.add(normalizeKey(key)));
    return normalized;
  }

  private String normalizeKey(String key) {
    return key.trim().toLowerCase(Locale.ROOT);
  }

  private BusinessRuleViolationException rule(String message) {
    return new BusinessRuleViolationException(message);
  }

  private interface Expression {
    BigDecimal evaluate(Evaluation evaluation, int repetition);

    Set<String> references();
  }

  private record NumberExpression(BigDecimal value) implements Expression {
    @Override
    public BigDecimal evaluate(Evaluation evaluation, int repetition) {
      return value;
    }

    @Override
    public Set<String> references() {
      return Set.of();
    }
  }

  private record ReferenceExpression(String key) implements Expression {
    @Override
    public BigDecimal evaluate(Evaluation evaluation, int repetition) {
      return evaluation.resolve(key, repetition);
    }

    @Override
    public Set<String> references() {
      return Set.of(key);
    }
  }

  private record UnaryExpression(char operator, Expression value) implements Expression {
    @Override
    public BigDecimal evaluate(Evaluation evaluation, int repetition) {
      BigDecimal evaluated = value.evaluate(evaluation, repetition);
      return operator == '-' ? evaluated.negate() : evaluated;
    }

    @Override
    public Set<String> references() {
      return value.references();
    }
  }

  private record BinaryExpression(char operator, Expression left, Expression right)
      implements Expression {
    @Override
    public BigDecimal evaluate(Evaluation evaluation, int repetition) {
      BigDecimal first = left.evaluate(evaluation, repetition);
      BigDecimal second = right.evaluate(evaluation, repetition);
      return switch (operator) {
        case '+' -> first.add(second);
        case '-' -> first.subtract(second);
        case '*' -> first.multiply(second);
        case '/' -> {
          if (second.signum() == 0) {
            throw new BusinessRuleViolationException("La fórmula intentó dividir entre cero.");
          }
          yield first.divide(second, INTERNAL_SCALE, RoundingMode.HALF_UP);
        }
        default -> throw new IllegalStateException("Operador no soportado.");
      };
    }

    @Override
    public Set<String> references() {
      Set<String> result = new HashSet<>(left.references());
      result.addAll(right.references());
      return result;
    }
  }

  private record PercentExpression(Expression value) implements Expression {
    @Override
    public BigDecimal evaluate(Evaluation evaluation, int repetition) {
      return value.evaluate(evaluation, repetition).divide(BigDecimal.valueOf(100));
    }

    @Override
    public Set<String> references() {
      return value.references();
    }
  }

  private record FunctionExpression(String name, List<Expression> arguments) implements Expression {
    private FunctionExpression {
      int size = arguments.size();
      switch (name) {
        case "SUM", "AVG", "MIN", "MAX", "COUNT" -> requireSize(name, size, 1, Integer.MAX_VALUE);
        case "ROUND" -> requireSize(name, size, 1, 2);
        case "PERCENT" -> requireSize(name, size, 2, 2);
        default -> throw new BusinessRuleViolationException("Función no permitida: " + name + ".");
      }
    }

    private static void requireSize(String name, int size, int minimum, int maximum) {
      if (size < minimum || size > maximum) {
        String expected =
            minimum == maximum ? Integer.toString(minimum) : minimum + " o " + maximum;
        throw new BusinessRuleViolationException(
            "La función %s requiere %s argumento(s).".formatted(name, expected));
      }
    }

    @Override
    public BigDecimal evaluate(Evaluation evaluation, int repetition) {
      return switch (name) {
        case "SUM", "AVG", "MIN", "MAX", "COUNT" -> aggregate(evaluation, repetition);
        case "ROUND" -> round(evaluation, repetition);
        case "PERCENT" -> percent(evaluation, repetition);
        default -> throw new IllegalStateException("Función no soportada.");
      };
    }

    private BigDecimal aggregate(Evaluation evaluation, int repetition) {
      if (arguments.isEmpty()) {
        throw new BusinessRuleViolationException("La función " + name + " requiere argumentos.");
      }
      List<BigDecimal> values = new ArrayList<>();
      List<Integer> targetRepetitions =
          evaluation.usesAllRepetitions() ? evaluation.repetitions() : List.of(repetition);
      for (int targetRepetition : targetRepetitions) {
        arguments.forEach(argument -> values.add(argument.evaluate(evaluation, targetRepetition)));
      }
      return switch (name) {
        case "SUM" -> values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        case "AVG" ->
            values.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), INTERNAL_SCALE, RoundingMode.HALF_UP);
        case "MIN" -> values.stream().min(BigDecimal::compareTo).orElseThrow();
        case "MAX" -> values.stream().max(BigDecimal::compareTo).orElseThrow();
        case "COUNT" -> BigDecimal.valueOf(values.size());
        default -> throw new IllegalStateException("Agregado no soportado.");
      };
    }

    private BigDecimal round(Evaluation evaluation, int repetition) {
      requireArguments(1, 2);
      int scale =
          arguments.size() == 2
              ? arguments.get(1).evaluate(evaluation, repetition).intValueExact()
              : 0;
      if (scale < 0 || scale > 10) {
        throw new BusinessRuleViolationException("ROUND admite una precisión entre 0 y 10.");
      }
      return arguments
          .get(0)
          .evaluate(evaluation, repetition)
          .setScale(scale, RoundingMode.HALF_UP);
    }

    private BigDecimal percent(Evaluation evaluation, int repetition) {
      requireArguments(2, 2);
      BigDecimal total = arguments.get(1).evaluate(evaluation, repetition);
      if (total.signum() == 0) {
        throw new BusinessRuleViolationException("PERCENT no admite un total igual a cero.");
      }
      return arguments
          .get(0)
          .evaluate(evaluation, repetition)
          .multiply(BigDecimal.valueOf(100))
          .divide(total, INTERNAL_SCALE, RoundingMode.HALF_UP);
    }

    private void requireArguments(int minimum, int maximum) {
      if (arguments.size() < minimum || arguments.size() > maximum) {
        throw new BusinessRuleViolationException(
            "La función %s requiere %s argumento(s).".formatted(name, minimum));
      }
    }

    @Override
    public Set<String> references() {
      Set<String> result = new HashSet<>();
      arguments.forEach(argument -> result.addAll(argument.references()));
      return result;
    }
  }

  private static final class Evaluation {
    private final Map<String, Definition> definitions;
    private final Map<String, Expression> expressions;
    private final Map<String, Map<Integer, BigDecimal>> captured;
    private final List<Integer> repetitions;
    private final Map<String, BigDecimal> memo = new HashMap<>();
    private boolean aggregateFunctions;

    private Evaluation(
        Map<String, Definition> definitions,
        Map<String, Expression> expressions,
        Map<String, Map<Integer, BigDecimal>> captured,
        List<Integer> repetitions) {
      this.definitions = definitions;
      this.expressions = expressions;
      this.captured = captured;
      this.repetitions = repetitions;
    }

    private BigDecimal resolve(String rawKey, int repetition) {
      String key = rawKey.toLowerCase(Locale.ROOT);
      Definition definition = definitions.get(key);
      int targetRepetition =
          definition != null && definition.scope() == CalculationScope.AGGREGATE ? 0 : repetition;
      String memoKey = key + ":" + targetRepetition;
      if (memo.containsKey(memoKey)) {
        return memo.get(memoKey);
      }
      Map<Integer, BigDecimal> values = captured.get(key);
      if (values != null) {
        BigDecimal value = values.get(targetRepetition);
        if (value == null && values.size() == 1) {
          value = values.values().iterator().next();
        }
        if (value == null) {
          throw new BusinessRuleViolationException(
              "No existe un valor para «%s» en la lectura %d."
                  .formatted(key, targetRepetition + 1));
        }
        return value;
      }
      Expression expression = expressions.get(key);
      if (expression == null) {
        throw new BusinessRuleViolationException("No existe el campo numérico «" + key + "».");
      }
      boolean previousAggregateFunctions = aggregateFunctions;
      aggregateFunctions = definition.scope() == CalculationScope.AGGREGATE;
      BigDecimal calculated;
      try {
        calculated = expression.evaluate(this, targetRepetition);
      } finally {
        aggregateFunctions = previousAggregateFunctions;
      }
      memo.put(memoKey, calculated);
      return calculated;
    }

    private Map<String, List<BigDecimal>> inputs(
        Set<String> references, int repetition, CalculationScope calculationScope) {
      Map<String, List<BigDecimal>> result = new LinkedHashMap<>();
      references.stream()
          .sorted()
          .forEach(
              reference -> {
                Definition definition = definitions.get(reference);
                List<Integer> indexes =
                    definition != null && definition.scope() == CalculationScope.AGGREGATE
                        ? List.of(0)
                        : calculationScope == CalculationScope.AGGREGATE
                            ? repetitions
                            : List.of(repetition);
                result.put(
                    reference, indexes.stream().map(index -> resolve(reference, index)).toList());
              });
      return result;
    }

    private List<Integer> repetitions() {
      return repetitions;
    }

    private boolean usesAllRepetitions() {
      return aggregateFunctions;
    }
  }

  private static final class Parser {
    private final String source;
    private int position;

    private Parser(String source) {
      this.source = source;
    }

    private Expression parse() {
      Expression expression = expression();
      whitespace();
      if (position != source.length()) {
        fail("Símbolo no reconocido en la posición " + (position + 1) + ".");
      }
      return expression;
    }

    private Expression expression() {
      Expression value = term();
      while (true) {
        whitespace();
        if (take('+')) value = new BinaryExpression('+', value, term());
        else if (take('-')) value = new BinaryExpression('-', value, term());
        else return value;
      }
    }

    private Expression term() {
      Expression value = unary();
      while (true) {
        whitespace();
        if (take('*')) value = new BinaryExpression('*', value, unary());
        else if (take('/')) value = new BinaryExpression('/', value, unary());
        else return value;
      }
    }

    private Expression unary() {
      whitespace();
      if (take('+')) return new UnaryExpression('+', unary());
      if (take('-')) return new UnaryExpression('-', unary());
      Expression value = primary();
      whitespace();
      return take('%') ? new PercentExpression(value) : value;
    }

    private Expression primary() {
      whitespace();
      if (take('(')) {
        Expression value = expression();
        whitespace();
        if (!take(')')) fail("Falta cerrar un paréntesis.");
        return value;
      }
      if (position < source.length()
          && (Character.isDigit(source.charAt(position)) || source.charAt(position) == '.')) {
        return number();
      }
      String identifier = identifier();
      whitespace();
      if (!take('(')) {
        return new ReferenceExpression(identifier.toLowerCase(Locale.ROOT));
      }
      List<Expression> arguments = new ArrayList<>();
      whitespace();
      if (!take(')')) {
        do {
          arguments.add(expression());
          whitespace();
        } while (take(','));
        if (!take(')')) fail("Falta cerrar la función " + identifier + ".");
      }
      return new FunctionExpression(identifier.toUpperCase(Locale.ROOT), arguments);
    }

    private Expression number() {
      int start = position;
      while (position < source.length()
          && (Character.isDigit(source.charAt(position)) || source.charAt(position) == '.')) {
        position++;
      }
      try {
        return new NumberExpression(new BigDecimal(source.substring(start, position)));
      } catch (NumberFormatException exception) {
        fail("Número inválido en la fórmula.");
        return null;
      }
    }

    private String identifier() {
      whitespace();
      int start = position;
      if (position >= source.length()
          || (!Character.isLetter(source.charAt(position)) && source.charAt(position) != '_')) {
        fail("Se esperaba un número, campo o función en la posición " + (position + 1) + ".");
      }
      position++;
      while (position < source.length()
          && (Character.isLetterOrDigit(source.charAt(position))
              || source.charAt(position) == '_')) {
        position++;
      }
      return source.substring(start, position);
    }

    private boolean take(char expected) {
      if (position < source.length() && source.charAt(position) == expected) {
        position++;
        return true;
      }
      return false;
    }

    private void whitespace() {
      while (position < source.length() && Character.isWhitespace(source.charAt(position))) {
        position++;
      }
    }

    private void fail(String message) {
      throw new BusinessRuleViolationException("Fórmula inválida: " + message);
    }
  }
}
