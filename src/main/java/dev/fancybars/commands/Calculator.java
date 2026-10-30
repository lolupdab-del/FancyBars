package dev.fancybars.commands;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Tiny maths parser for the calculator command: + - * / % ^, brackets, decimals, commas, and SkyBlock style
 * suffixes (5k, 2.5m, 1b, 3t). "x" also means times. Example: 5M/5b, (1.2m + 300k) * 3, 2^10.
 */
public final class Calculator {
	private final String s;
	private int pos;

	private Calculator(String s) {
		this.s = s.replace(" ", "").replace("_", "");
	}

	public static double evaluate(String expression) {
		Calculator c = new Calculator(expression);
		double value = c.expr();
		if (c.pos < c.s.length()) throw new IllegalArgumentException("Unexpected '" + c.s.charAt(c.pos) + "'");
		return value;
	}

	private boolean eat(char ch) {
		if (pos < s.length() && Character.toLowerCase(s.charAt(pos)) == ch) {
			pos++;
			return true;
		}
		return false;
	}

	private double expr() {
		double v = term();
		while (true) {
			if (eat('+')) v += term();
			else if (eat('-')) v -= term();
			else return v;
		}
	}

	private double term() {
		double v = power();
		while (true) {
			if (eat('*') || eat('x') || eat('×')) v *= power();
			else if (eat('/') || eat('÷')) {
				double d = power();
				if (d == 0) throw new ArithmeticException("Can't divide by zero");
				v /= d;
			} else if (eat('%')) v %= power();
			else return v;
		}
	}

	private double power() {
		double base = unary();
		if (eat('^')) return Math.pow(base, power());
		return base;
	}

	private double unary() {
		if (eat('-')) return -unary();
		if (eat('+')) return unary();
		return primary();
	}

	private double primary() {
		if (eat('(')) {
			double v = expr();
			if (!eat(')')) throw new IllegalArgumentException("Missing )");
			return suffix(v);
		}
		int start = pos;
		while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.' || s.charAt(pos) == ',')) pos++;
		if (start == pos) throw new IllegalArgumentException(pos < s.length() ? "Unexpected '" + s.charAt(pos) + "'" : "Missing a number");
		double v = Double.parseDouble(s.substring(start, pos).replace(",", ""));
		return suffix(v);
	}

	private double suffix(double v) {
		if (pos >= s.length()) return v;
		char c = Character.toLowerCase(s.charAt(pos));
		double mult = switch (c) {
			case 'k' -> 1e3;
			case 'm' -> 1e6;
			case 'b' -> 1e9;
			case 't' -> 1e12;
			default -> 1;
		};
		if (mult != 1) pos++;
		return v * mult;
	}

	/** 1234567.891 → "1,234,567.89". */
	public static String format(double v) {
		if (Double.isNaN(v) || Double.isInfinite(v)) return String.valueOf(v);
		DecimalFormat f = new DecimalFormat("#,##0.####", DecimalFormatSymbols.getInstance(Locale.US));
		return f.format(v);
	}

	/** 1234567 → "1.23M". */
	public static String shortForm(double v) {
		double a = Math.abs(v);
		String[] units = {"", "k", "M", "B", "T"};
		int i = 0;
		while (a >= 1000 && i < units.length - 1) {
			a /= 1000;
			i++;
		}
		if (i == 0) return "";
		DecimalFormat f = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.US));
		return (v < 0 ? "-" : "") + f.format(a) + units[i];
	}
}
