package com.denaliai.fw.json;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.io.JsonStringEncoder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

public abstract class JSONSerializer {
	public static final BooleanNode JSON_TRUE = new BooleanNode(true);
	public static final BooleanNode JSON_FALSE = new BooleanNode(false);
	public static final NullNode JSON_NULL = new NullNode();
	private static final JsonFactory m_factory;
	
	static {
		m_factory = new JsonFactory();
		//m_factory.disable(SerializationFeature.CLOSE_CLOSEABLE);
		m_factory.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
		m_factory.disable(JsonParser.Feature.AUTO_CLOSE_SOURCE);
	}

	public static ObjectNode parse(ByteBuf json) throws JsonParseException {
		try {
			return parse0(json);
		} catch(Exception ex) {
			throw new JsonParseException(ex);
		}
	}

	public static Node parseAny(ByteBuf json) throws JsonParseException {
		try {
			return parseAny0(json);
		} catch(Exception ex) {
			throw new JsonParseException(ex);
		}
	}

	private static Node parseAny0(ByteBuf json) throws Exception {
		JsonParser parser = m_factory.createParser((InputStream)new ByteBufInputStream(json));

		Node node = parseAny(parser);

		parser.close();
		return node;
	}

	private static ObjectNode parse0(ByteBuf json) throws Exception {
		JsonParser parser = m_factory.createParser((InputStream)new ByteBufInputStream(json));
		if (parser.nextToken() != JsonToken.START_OBJECT) {
			throw new JsonParseException("Expected data to start with an Object");
		}

		ObjectNode node = parseObject(parser);

		parser.close();
		return node;
	}

	private static Node parseAny(JsonParser parser) throws Exception {
		JsonToken token = parser.nextToken();
		if (token.isStructStart()) {
			if (token == JsonToken.START_ARRAY) {
				return parseArray(parser);
			} else {
				return parseObject(parser);
			}
		} else if (token.isBoolean()) {
			if (token == JsonToken.VALUE_TRUE) {
				return JSON_TRUE;
			} else {
				return JSON_FALSE;
			}
		} else if (token.isNumeric()) {
			if (token == JsonToken.VALUE_NUMBER_INT) {
				Node newNode;
				try {
					newNode = new IntegerNode(parser.getLongValue());
				} catch(com.fasterxml.jackson.core.JsonParseException ex) {
					// JSON can contain integer values greater than the size of a Java long, so treat those as Strings for now
					newNode = new StringNode(parser.getText());
				}
				return newNode;
			} else {
				return new FloatingNode(parser.getDoubleValue());
			}
		} else if (token == JsonToken.VALUE_NULL) {
			return JSON_NULL;

		} else {
			return new StringNode(parser.getText());
		}
	}

	private static ObjectNode parseObject(JsonParser parser) throws Exception {
		ObjectNode node = new ObjectNode();
		while (true) {
			JsonToken token = parser.nextToken();
			if (token == JsonToken.END_OBJECT) {
				break;
			}
			String fieldName = parser.getCurrentName();

			token = parser.nextToken();
			if (token.isStructStart()) {
				if (token == JsonToken.START_ARRAY) {
					node.add(fieldName, parseArray(parser));
				} else {
					node.add(fieldName, parseObject(parser));
				}
			} else if (token.isBoolean()) {
				if (token == JsonToken.VALUE_TRUE) {
					node.add(fieldName, JSON_TRUE);
				} else {
					node.add(fieldName, JSON_FALSE);
				}
			} else if (token.isNumeric()) {
				if (token == JsonToken.VALUE_NUMBER_INT) {
					Node newNode;
					try {
						newNode = new IntegerNode(parser.getLongValue());
					} catch(com.fasterxml.jackson.core.JsonParseException ex) {
						// JSON can contain integer values greater than the size of a Java long, so treat those as Strings for now
						newNode = new StringNode(parser.getText());
					}
					node.add(fieldName, newNode);
				} else {
					node.add(fieldName, new FloatingNode(parser.getDoubleValue()));
				}
			} else if (token == JsonToken.VALUE_NULL) {
				node.add(fieldName, JSON_NULL);

			} else {
				node.add(fieldName, new StringNode(parser.getText()));
			}
		}
		return node;
	}


	private static ArrayNode parseArray(JsonParser parser) throws Exception {
		ArrayNode node = new ArrayNode();
		while (true) {
			JsonToken token = parser.nextToken();
			if (token == JsonToken.END_ARRAY) {
				break;
			}
			if (token.isStructStart()) {
				if (token == JsonToken.START_ARRAY) {
					node.add(parseArray(parser));
				} else {
					node.add(parseObject(parser));
				}
			} else if (token.isBoolean()) {
				if (token == JsonToken.VALUE_TRUE) {
					node.add(JSON_TRUE);
				} else {
					node.add(JSON_FALSE);
				}
			} else if (token.isNumeric()) {
				if (token == JsonToken.VALUE_NUMBER_INT) {
					node.add(new IntegerNode(parser.getLongValue()));
				} else {
					node.add(new FloatingNode(parser.getDoubleValue()));
				}
			} else if (token == JsonToken.VALUE_NULL) {
				node.add(JSON_NULL);

			} else {
				node.add(new StringNode(parser.getText()));
			}
		}
		return node;
	}

	public static abstract class Node {
		public boolean isNull() {
			return false;
		}

		public boolean isString() {
			return false;
		}

		public boolean isNumeric() {
			return false;
		}

		public boolean isInteger() {
			return false;
		}

		public boolean isFloatingPoint() {
			return false;
		}

		public boolean isBoolean() {
			return false;
		}

		public boolean isArray() {
			return false;
		}

		public boolean isObject() {
			return false;
		}

		public String toString() {
			return "";
		}

		public ObjectNode asObject() throws JsonUseException {
			if (!(this instanceof ObjectNode)) {
				throw new JsonUseException("Node is not a JSON object");
			}
			return (ObjectNode) this;
		}

		public StringNode asString() throws JsonUseException {
			if (!(this instanceof StringNode)) {
				throw new JsonUseException("Node is not a JSON string");
			}
			return (StringNode) this;
		}

		public NumericNode asNumeric() throws JsonUseException {
			if (!(this instanceof NumericNode)) {
				throw new JsonUseException("Node is not a JSON number");
			}
			return (NumericNode) this;
		}

		public IntegerNode asInteger() throws JsonUseException {
			if (!(this instanceof IntegerNode)) {
				throw new JsonUseException("Node is not a JSON integer");
			}
			return (IntegerNode) this;
		}

		public FloatingNode asFloating() throws JsonUseException {
			if (!(this instanceof FloatingNode)) {
				throw new JsonUseException("Node is not a JSON floating point number");
			}
			return (FloatingNode) this;
		}

		public ArrayNode asArray() throws JsonUseException {
			if (!(this instanceof ArrayNode)) {
				throw new JsonUseException("Node is not a JSON array");
			}
			return (ArrayNode) this;
		}

		public BooleanNode asBoolean() throws JsonUseException {
			if (!(this instanceof BooleanNode)) {
				throw new JsonUseException("Node is not a JSON boolean");
			}
			return (BooleanNode) this;
		}

		public long toLongValue() throws JsonUseException {
			if (this instanceof NumericNode) {
				return ((NumericNode)this).toLong();
			}
			if (!(this instanceof StringNode)) {
				throw new JsonUseException("Node of type " + getClass().getSimpleName() + " cannot be converted to a long");
			}
			try {
				return Long.parseLong(((StringNode)this).m_value);
			} catch(Exception ex) {
				throw new JsonUseException("StringNode value cannot be converted to a long");
			}
		}

		public abstract void writeJson(ByteBuf dest);
	}

	public static final class NullNode extends Node {
		private NullNode() {
		}

		@Override
		public boolean isNull() {
			return true;
		}

		@Override
		public String toString() {
			return "null";
		}

		@Override
		public void writeJson(ByteBuf dest) {
			dest.writeCharSequence("null", StandardCharsets.US_ASCII);
		}

		@Override
		public ObjectNode asObject() throws JsonUseException {
			throw new JsonUseException("Node is null and not a JSON object");
		}

		@Override
		public StringNode asString() throws JsonUseException {
			throw new JsonUseException("Node is null and not a JSON string");
		}

		@Override
		public NumericNode asNumeric() throws JsonUseException {
			throw new JsonUseException("Node is null and not a JSON number");
		}

		@Override
		public IntegerNode asInteger() throws JsonUseException {
			throw new JsonUseException("Node is null and not a JSON integer");
		}

		@Override
		public FloatingNode asFloating() throws JsonUseException {
			throw new JsonUseException("Node is null and not a JSON floating point number");
		}

		@Override
		public ArrayNode asArray() throws JsonUseException {
			throw new JsonUseException("Node is null and not a JSON array");
		}

		@Override
		public BooleanNode asBoolean() throws JsonUseException {
			throw new JsonUseException("Node is null and not a JSON boolean");
		}

	}

	public static final class StringNode extends Node {
		private String m_value;
		private String m_toString;

		public StringNode(String value) {
			m_value = value;
		}
		public StringNode(long value) {
			m_value = Long.toString(value);
		}

		@Override
		public boolean isString() {
			return true;
		}

		public String value() {
			return m_value;
		}

		@Override
		public String toString() {
			if (m_toString == null) {
				m_toString = "\"" + m_value + "\"";
			}
			return m_toString;
		}

		@Override
		public void writeJson(ByteBuf dest) {
			dest.writeByte('"');
			dest.writeBytes(JsonStringEncoder.getInstance().quoteAsUTF8(m_value));
			dest.writeByte('"');
		}
	}

	public static final class BooleanNode extends Node {
		private final Boolean m_value;

		public BooleanNode(boolean value) {
			m_value = value ? Boolean.TRUE : Boolean.FALSE;
		}

		@Override
		public boolean isBoolean() {
			return true;
		}

		public Boolean value() {
			return m_value;
		}

		public boolean isTrue() {
			return m_value == Boolean.TRUE;
		}

		public boolean isFalse() {
			return m_value == Boolean.FALSE;
		}

		@Override
		public String toString() {
			return m_value.toString();
		}

		@Override
		public void writeJson(ByteBuf dest) {
			dest.writeCharSequence(m_value.toString(), StandardCharsets.US_ASCII);
		}
	}

	public static abstract class NumericNode extends Node {

		@Override
		public boolean isNumeric() {
			return true;
		}

		public abstract int toInt() throws JsonUseException;
		public abstract long toLong() throws JsonUseException;
		public abstract float toFloat() throws JsonUseException;
		public abstract double toDouble() throws JsonUseException;
	}

	public static final class IntegerNode extends NumericNode {
		private final long m_value;

		public IntegerNode(long value) {
			m_value = value;
		}

		@Override
		public boolean isInteger() {
			return true;
		}

		@Override
		public int toInt() throws JsonUseException {
			if (m_value > (long)Integer.MAX_VALUE || m_value < (long)Integer.MIN_VALUE) {
				throw new JsonUseException("Value is too big and overflows an integer");
			}
			return (int)m_value;
		}

		@Override
		public long toLong() {
			return m_value;
		}

		@Override
		public float toFloat() throws JsonUseException {
			return (float)m_value;
		}

		@Override
		public double toDouble() throws JsonUseException {
			return (double)m_value;
		}

		@Override
		public void writeJson(ByteBuf dest) {
			dest.writeCharSequence(Long.toString(m_value), StandardCharsets.US_ASCII);
		}
	}

	public static final class FloatingNode extends NumericNode {
		private final double m_value;

		public FloatingNode(double value) {
			m_value = value;
		}

		@Override
		public boolean isFloatingPoint() {
			return true;
		}

		@Override
		public float toFloat() throws JsonUseException {
			return (float)m_value;
		}

		@Override
		public double toDouble() {
			return m_value;
		}

		@Override
		public int toInt() {
			return (int)m_value;
		}

		@Override
		public long toLong() {
			return (long)m_value;
		}

		@Override
		public void writeJson(ByteBuf dest) {
			dest.writeCharSequence(Double.toString(m_value), StandardCharsets.US_ASCII);
		}
	}

	public static final class ObjectNode extends Node implements Iterable<Map.Entry<String,Node>>{
		private final Map<String, Node> m_children = new HashMap<>();

		@Override
		public boolean isObject() {
			return true;
		}

		public int size() {
			return m_children.size();
		}

		public void add(String name, Node value) {
			m_children.put(name, value);
		}

		public void remove(String name) {
			m_children.remove(name);
		}

		public Node get(String name) {
			return m_children.get(name);
		}

		public Node nullableGet(String name) {
			Node n = m_children.get(name);
			if (n == null) {
				return JSONSerializer.JSON_NULL;
			}
			return n;
		}

		@Override
		public Iterator<Map.Entry<String, Node>> iterator() {
			return m_children.entrySet().iterator();
		}

		@Override
		public void writeJson(ByteBuf dest) {
			boolean first = true;
			dest.writeByte('{');
			for(Map.Entry<String, Node> entry : m_children.entrySet()) {
				if (!first) {
					dest.writeByte(',');
				}
				dest.writeByte('"');
				dest.writeCharSequence(entry.getKey(), StandardCharsets.US_ASCII);
				dest.writeByte('"');
				dest.writeByte(':');
				entry.getValue().writeJson(dest);
				first = false;
			}
			dest.writeByte('}');
		}
	}

	public static final class ArrayNode extends Node implements Iterable<Node> {
		private final List<Node> m_list = new ArrayList<Node>(32);

		@Override
		public boolean isArray() {
			return true;
		}

		public int size() {
			return m_list.size();
		}

		public void add(Node value) {
			m_list.add(value);
		}

		public Node get(int index) {
			return m_list.get(index);
		}

		@Override
		public Iterator<Node> iterator() {
			return m_list.iterator();
		}

		@Override
		public void writeJson(ByteBuf dest) {
			boolean first = true;
			dest.writeByte('[');
			for(Node n : m_list) {
				if (!first) {
					dest.writeByte(',');
				}
				n.writeJson(dest);
				first = false;
			}
			dest.writeByte(']');
		}
	}

	public static final class JsonParseException extends Exception {
		public JsonParseException(String message) {
			super(message);
		}
		public JsonParseException(Throwable cause) {
			super(cause);
		}
	}

	public static final class JsonUseException extends Exception {
		public JsonUseException(String message) {
			super(message);
		}
	}
}
