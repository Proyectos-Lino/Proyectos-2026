package com.admision.lector_dbf.evaluation.service;

import com.admision.lector_dbf.shared.ApiException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.function.UnaryOperator;

/** Interpreta únicamente expresiones aritméticas en función del puntaje original. */
public final class OfficialScoreFormula {
    public static final int MAX_LENGTH = 200;
    private static final MathContext PRECISION = MathContext.DECIMAL128;
    private static final BigDecimal LIMIT = new BigDecimal("100000000000");

    private OfficialScoreFormula() {}

    public static UnaryOperator<BigDecimal> compile(String expression) {
        if (expression == null) return result -> null;
        if (expression.isBlank() || expression.length() > MAX_LENGTH)
            throw ApiException.badRequest("Indica una fórmula de hasta 200 caracteres");
        Parser parser = new Parser(expression);
        Term formula = parser.expression();
        if (parser.next() != '\0' || parser.position != expression.length())
            throw parser.invalid();
        return result -> {
            BigDecimal score = formula.calculate().apply(result).setScale(3, RoundingMode.HALF_UP);
            if (score.abs().compareTo(LIMIT) >= 0)
                throw ApiException.badRequest("La fórmula genera una nota fuera del rango permitido");
            return score;
        };
    }

    private record Term(UnaryOperator<BigDecimal> calculate, boolean constant) {
        private static Term constant(BigDecimal value) {
            return new Term(result -> value, true);
        }
    }

    private static BigDecimal operate(char operator, BigDecimal left, BigDecimal right) {
        return switch (operator) {
            case '+' -> left.add(right, PRECISION);
            case '-' -> left.subtract(right, PRECISION);
            case '*' -> left.multiply(right, PRECISION);
            case '/' -> {
                if (right.signum() == 0)
                    throw ApiException.badRequest("La fórmula contiene una división por cero");
                yield left.divide(right, PRECISION);
            }
            default -> throw new IllegalArgumentException("Operador aritmético desconocido");
        };
    }

    private static final class Parser {
        private final String input;
        private int position;

        private Parser(String input) { this.input = input; }

        private Term expression() {
            Term left = product();
            while (next() == '+' || next() == '-') {
                char operator = input.charAt(position++);
                left = combine(operator, left, product());
            }
            return left;
        }

        private Term product() {
            Term left = atom();
            while (next() == '*' || next() == '/') {
                char operator = input.charAt(position++);
                left = combine(operator, left, atom());
            }
            return left;
        }

        private Term atom() {
            char token = next();
            if (token == '+' || token == '-') {
                position++;
                Term value = atom();
                return token == '+' ? value : combine('-', Term.constant(BigDecimal.ZERO), value);
            }
            if (token == '(') {
                position++;
                Term value = expression();
                if (next() != ')') throw invalid();
                position++;
                return value;
            }
            if (input.regionMatches(true, position, "Resultado", 0, "Resultado".length())) {
                position += "Resultado".length();
                return new Term(result -> result, false);
            }
            int start = position;
            while (position < input.length() && (isDigit(input.charAt(position)) || input.charAt(position) == '.'
                    || input.charAt(position) == ',')) position++;
            if (start == position) throw invalid();
            try {
                return Term.constant(new BigDecimal(input.substring(start, position).replace(',', '.'), PRECISION));
            } catch (NumberFormatException exception) {
                throw invalid();
            }
        }

        private Term combine(char operator, Term left, Term right) {
            if (operator == '/' && right.constant() && right.calculate().apply(BigDecimal.ZERO).signum() == 0)
                throw ApiException.badRequest("La fórmula contiene una división por cero");
            UnaryOperator<BigDecimal> calculate = result ->
                    operate(operator, left.calculate().apply(result), right.calculate().apply(result));
            return left.constant() && right.constant()
                    ? Term.constant(calculate.apply(BigDecimal.ZERO)) : new Term(calculate, false);
        }

        private char next() {
            while (position < input.length() && Character.isWhitespace(input.charAt(position))) position++;
            return position == input.length() ? '\0' : input.charAt(position);
        }

        private boolean isDigit(char value) { return value >= '0' && value <= '9'; }

        private ApiException invalid() {
            return ApiException.badRequest("Fórmula inválida. Usa Resultado, números, paréntesis y los operadores +, -, * y /");
        }
    }
}
