package emerald;

import java.util.ArrayList;
import java.util.HashMap;                                                 
import java.util.List;                                                    
import java.util.Map;
import java.util.Stack;

import static emerald.TokenType.*;

public class Scanner
{
	private final String source;
	private final List<Token> tokens = new ArrayList<>();
	private int start = 0;
	private int current = 0;
	private int line = 1;
	private int indent = 0;
	private int altIndent = 0;
	private Stack<Integer> indents = new Stack<Integer>();
	private Stack<Integer> altIndents = new Stack<Integer>();
	private Stack<Integer> brackets = new Stack<Integer>();
	private boolean atbol = true;
	
	private static final Map<String, TokenType> keywords;
	
	static {
		keywords = new HashMap<>();
		keywords.put("and",      AND);
		keywords.put("else",     ELSE);
		keywords.put("elsif", 	 ELSEIF);
		keywords.put("false",    FALSE);
		keywords.put("for",      FOR);
		keywords.put("fn", 		 FN);
		keywords.put("in", 		 IN);
		keywords.put("if",       IF);
		keywords.put("import", 	 IMPORT);
		keywords.put("nil",      NIL);
		keywords.put("or",       OR);
		keywords.put("print",    PRINT);
		keywords.put("return",   RETURN);
		keywords.put("true",     TRUE);
		keywords.put("var",      VAR);
		keywords.put("while",    WHILE);
		keywords.put("end",      END);
	}
	
	Scanner(String source) {
		this.source = source;
		indents.push(indent);
		altIndents.push(altIndent);
	}
	
	List<Token> scanTokens() {
		while (!isAtEnd()) {
			start = current;
			scanToken();
		}
		
		if (!brackets.isEmpty()) {
			for (int i = 0; i < brackets.capacity() - 1; i++) {
				Emerald.error(brackets.get(i), "Unclosed bracket.");
			}
		}
		
		tokens.add(new Token(EOF, null, "", line));
		return tokens;
	}
	
	private boolean isAtEnd() {
		return current >= source.length();
	}
	
	private void scanToken() {
		char c = advance();
		
		int prevLine = line;
		
		// Parse whitespace/indentation at beginning of line
		if (atbol) {
			indent = 0;
			altIndent = 0;
			
			while (true) {
				if (isAtEnd()) break;
				
				if (c == '\t') {
					System.out.println("Matched tab");
					indent++;
					for (int i = 0; i < 8; i++) {
						if (indent % 8 == 0) break;
						
						indent++;
					}
					
					altIndent++;
				} else if (c == ' ') {
					indent++;
					altIndent++;
				} else {
					break;
				}
				
				c = advance();
			}
			
			if (indent == indents.peek() && altIndent == altIndents.peek()) {
				// Do nothing, indentation is the same.
			} else if (indent > indents.peek() && altIndent > altIndents.peek()) {
				addToken(INDENT, previous()); // Indentation.
				indents.push(indent);
				altIndents.push(altIndent);
			} else if (indent < indents.peek() && altIndent < altIndents.peek()) {
				boolean found = false; // Dedentation.
				
				for (int i = 0; i < indents.size(); i++) {
					if (indent == indents.peek() && altIndent == altIndents.peek()) {
						found = true;
						break;
					}
					
					indents.pop();
					altIndents.pop();
					addToken(DEDENT, previous());
				}
				
				if (!found) {
					Emerald.error(line, "Did not find previous block with same indentation.");
				}
			} else {
				Emerald.error(line, "Indentation is invalid.");
			}
			
			atbol = false;
		}
		
		switch (c) {
			case '(': addToken(LEFT_PAREN); brackets.push(line); break;
			case ')': addToken(RIGHT_PAREN); brackets.pop(); break;
			case '{': addToken(LEFT_BRACE); brackets.push(line); break;
			case '}': addToken(RIGHT_BRACE); brackets.pop(); break;
			case '[': addToken(LEFT_SQUARE); brackets.push(line); break;
			case ']': addToken(RIGHT_SQUARE); brackets.pop(); break;
			case ',': addToken(COMMA); break;
			case '.':
				if (match('.') && match('.')) {
					addToken(ELIPSIS);
				} else {
					addToken(DOT);
				}
				break;
			case '-':
				if (peek() == '-') {
					advance();
					addToken(MINUS_MINUS);
					break;
				}

				addToken(MINUS);
				break;
			case '+':
				if (peek() == '+') {
					advance();
					addToken(PLUS_PLUS);
					break;
				}
				
				addToken(PLUS);
				break;
			case '^': addToken(BIT_XOR); break;
			case '&': addToken(match('&') ? AND : BIT_AND); break;
			case '|': addToken(match('|') ? OR : BIT_OR); break;
			case ';': addToken(SEMICOLON); break;
			case ':': addToken(COLON); break;
			case '*': addToken(match('*') ? EXPONENT : STAR); break;
			case '?': addToken(QUESTION); break;
			
			case '!': addToken(match('=') ? BANG_EQUAL : BANG); break;
			case '=': addToken(match('=') ? EQUAL_EQUAL : EQUAL); break;
			case '<': addToken(match('=') ? (match('>') ? SIGN : LESS_EQUAL) : LESS); break;
			case '>': addToken(match('=') ? GREATER_EQUAL : GREATER); break;
			
			case '$': addToken(SIGIL); break;
			
			case '/':
				if (match('/')) {
					// A comment goes until the end of the line.
					while (peek() != '\n' && !isAtEnd()) advance();
				} else if (match('*')) {
					// Multiline comments
					while (peek() != '*' && peekNext() != '/' && !isAtEnd()) advance();
					advance();
				} else {
					addToken(SLASH);
				}
				break;
				
			case '#':
				if (match('*')) {
					while (peek() != '*' && peekNext() != '#' && !isAtEnd()) advance();
					advance();
				} else {
					while (peek() != '\n' && !isAtEnd()) advance();
				}
				break;
			
			case '"': string(); break;
			
			case '\r':
			case ' ':
			case '\t':
				break;
			
			case '\n':
				if (brackets.isEmpty() && previous() != '\\') {
					//addToken(NEWLINE);
					atbol = true;
				}
				line++;
				break;
			
			default:
				if (isDigit(c)) {
					number();
				} else if (isAlpha(c)) {
					identifier();
				} else {
					Emerald.error(line, "Unexpected character");
				}
				break;
		}
		
		if (prevLine == line) {
			atbol = false;
		}
	}
	
	private void string() {
		while (peek() != '"' && !isAtEnd()) {
			if (peek() == '\n') line++;
			advance();
		}
		
		if (isAtEnd()) {
			Emerald.error(line, "Unterminated string.");
			return;
		}
		
		// The closing "
		advance();
		
		// Trim the surrounding quotes.
		String value = source.substring(start + 1, current - 1);
		addToken(STRING, value);
	}
	
	private void number() {
		while (isDigit(peek())) advance();
		
		// Look for a fractional part
		if (peek() == '.' && isDigit(peekNext())) {
			// Consume the "."
			advance();
			
			while (isDigit(peek())) advance();
		}
		
		addToken(NUMBER, Double.parseDouble(source.substring(start, current)));
	}
	
	private boolean isDigit(char c) {
		return c >= '0' && c <= '9';
	}
	
	private void identifier() {
		while (isAlphaNumeric(peek())) advance();
		
		// See if the identifier is a reserved word
		String text = source.substring(start, current);
		
		TokenType type = keywords.get(text);
		if (type == null) type = IDENTIFIER;
		addToken(type);
	}
	
	private boolean isAlpha(char c) {
		return ((c >= 'a' && c <= 'z') ||
				(c >= 'A' && c <= 'Z') ||
				(c == '_'));
	}
	
	private boolean isAlphaNumeric(char c) {
		return isAlpha(c) || isDigit(c);
	}
	
	private char advance() {
		return source.charAt(current++);
	}
	
	private char previous() {
		return source.charAt(current - 1);
	}
	
	private char peek() {
		if (isAtEnd()) return '\0';
		return source.charAt(current);
	}
	
	private char peekNext() {
		if (current + 1 >= source.length()) return '\0';
		return source.charAt(current + 1);
	}
	
	private boolean match(char expected) {
		if (isAtEnd()) return false;
		if (source.charAt(current) != expected) return false;
		
		current++;
		return true;
	}
	
	private void addToken(TokenType type) {
		addToken(type, null);
	}
	
	private void addToken(TokenType type, Object literal) {
		String text = source.substring(start, current);
		tokens.add(new Token(type, text, literal, line));
	}
}
