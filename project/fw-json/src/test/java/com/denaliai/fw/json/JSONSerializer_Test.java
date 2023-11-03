package com.denaliai.fw.json;

import com.denaliai.fw.TestBase;
import io.netty.buffer.Unpooled;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class JSONSerializer_Test extends TestBase {

	private JSONSerializer.ObjectNode parse(String json) throws JSONSerializer.JsonParseException {
		return JSONSerializer.parse(Unpooled.wrappedBuffer(json.getBytes()));
	}
	private JSONSerializer.Node parseAny(String json) throws JSONSerializer.JsonParseException {
		return JSONSerializer.parseAny(Unpooled.wrappedBuffer(json.getBytes()));
	}

	@Test
	public void testEmptyObject() throws JSONSerializer.JsonParseException {
		JSONSerializer.ObjectNode n = parse("{}");
		Assertions.assertEquals(0, n.size());
	}

	@Test
	public void testAllNodeTypes() throws JSONSerializer.JsonParseException, JSONSerializer.JsonUseException {
		JSONSerializer.ObjectNode n = parse("{"
			+ "\"boolTrue\": true,"
			+ "\"boolFalse\": false,"
			+ "\"intA\": " + Integer.MIN_VALUE + ","
			+ "\"intB\": " + Integer.MAX_VALUE + ","
			+ "\"longA\": " + Long.MIN_VALUE + ","
			+ "\"longB\": " + Long.MAX_VALUE + ","
			+ "\"floatA\": " + Float.MIN_VALUE + ","
			+ "\"floatB\": " + Float.MAX_VALUE + ","
			+ "\"doubleA\": " + Double.MIN_VALUE + ","
			+ "\"doubleB\": " + Double.MAX_VALUE + ","
			+ "\"null\": null,"
			+ "\"array\": [true,10,1.0,null,{\"child\":\"value\"}, [1]],"
			+ "\"object\": {\"string\": \"value\"}"
			+ "}");
		Assertions.assertEquals(13, n.size());
		Assertions.assertEquals(JSONSerializer.JSON_TRUE, n.get("boolTrue"));
		Assertions.assertEquals(JSONSerializer.JSON_FALSE, n.get("boolFalse"));
		Assertions.assertEquals(Integer.MIN_VALUE, n.get("intA").asInteger().toInt());
		Assertions.assertEquals(Integer.MAX_VALUE, n.get("intB").asInteger().toInt());
		Assertions.assertEquals(Long.MIN_VALUE, n.get("longA").asInteger().toLong());
		Assertions.assertEquals(Long.MAX_VALUE, n.get("longB").asInteger().toLong());
		Assertions.assertEquals(Float.MIN_VALUE, n.get("floatA").asFloating().toFloat());
		Assertions.assertEquals(Float.MAX_VALUE, n.get("floatB").asFloating().toFloat());
		Assertions.assertEquals(Double.MIN_VALUE, n.get("doubleA").asFloating().toDouble());
		Assertions.assertEquals(Double.MAX_VALUE, n.get("doubleB").asFloating().toDouble());
		Assertions.assertThrows(JSONSerializer.JsonUseException.class, () -> n.get("longA").asInteger().toInt());
		Assertions.assertThrows(JSONSerializer.JsonUseException.class, () -> n.get("longB").asInteger().toInt());
		Assertions.assertEquals(JSONSerializer.JSON_NULL, n.get("null"));

		Assertions.assertEquals("value", n.get("object").asObject().get("string").asString().value());

		JSONSerializer.ArrayNode array = n.get("array").asArray();
		Assertions.assertEquals(6, array.size());
		Assertions.assertEquals(JSONSerializer.JSON_TRUE, array.get(0));
		Assertions.assertEquals(10, array.get(1).asNumeric().toInt());
		Assertions.assertEquals(1.0, array.get(2).asNumeric().toFloat());
		Assertions.assertEquals(JSONSerializer.JSON_NULL, array.get(3));
		Assertions.assertEquals(true, array.get(4).isObject());
		Assertions.assertEquals(true, array.get(5).isArray());

		JSONSerializer.ObjectNode n2 = array.get(4).asObject();
		Assertions.assertEquals("value", n2.get("child").asString().value());
		Assertions.assertEquals("\"value\"", n2.get("child").asString().toString());

		JSONSerializer.ArrayNode a2 = array.get(5).asArray();
		Assertions.assertEquals(1, a2.get(0).asNumeric().toInt());
	}

	@Test
	public void testEmptyString() throws JSONSerializer.JsonParseException {
		Assertions.assertThrows(JSONSerializer.JsonParseException.class, () -> parse(""));
	}

	@Test
	public void testMalformed1() throws JSONSerializer.JsonParseException {
		Assertions.assertThrows(JSONSerializer.JsonParseException.class, () -> parse("{"));
	}

	@Test
	public void testMalformed2() throws JSONSerializer.JsonParseException {
		Assertions.assertThrows(JSONSerializer.JsonParseException.class, () -> parse("}"));
	}

	@Test
	public void testMalformed3() throws JSONSerializer.JsonParseException {
		Assertions.assertThrows(JSONSerializer.JsonParseException.class, () -> {
			try {
				parse("{\"test\"}");
			} catch (JSONSerializer.JsonParseException ex) {
				LogManager.getLogger(JSONSerializer_Test.class).debug(ex);
				throw ex;
			}
		});
	}

	@Test
	public void testUnicode() throws Exception {
		JSONSerializer.ObjectNode node = parse("{\"test\": \"昨日\"}");
		Assertions.assertEquals("昨日", node.get("test").asString().value());
	}

	@Test
	public void testAllNodeTypesForAny() throws JSONSerializer.JsonParseException, JSONSerializer.JsonUseException {
		JSONSerializer.Node node = parseAny("{"
						+ "\"boolTrue\": true,"
						+ "\"boolFalse\": false,"
						+ "\"intA\": " + Integer.MIN_VALUE + ","
						+ "\"intB\": " + Integer.MAX_VALUE + ","
						+ "\"longA\": " + Long.MIN_VALUE + ","
						+ "\"longB\": " + Long.MAX_VALUE + ","
						+ "\"floatA\": " + Float.MIN_VALUE + ","
						+ "\"floatB\": " + Float.MAX_VALUE + ","
						+ "\"doubleA\": " + Double.MIN_VALUE + ","
						+ "\"doubleB\": " + Double.MAX_VALUE + ","
						+ "\"null\": null,"
						+ "\"array\": [true,10,1.0,null,{\"child\":\"value\"}, [1]],"
						+ "\"object\": {\"string\": \"value\"}"
						+ "}");
		JSONSerializer.ObjectNode n = node.asObject();
		Assertions.assertEquals(13, n.size());
		Assertions.assertEquals(JSONSerializer.JSON_TRUE, n.get("boolTrue"));
		Assertions.assertEquals(JSONSerializer.JSON_FALSE, n.get("boolFalse"));
		Assertions.assertEquals(Integer.MIN_VALUE, n.get("intA").asInteger().toInt());
		Assertions.assertEquals(Integer.MAX_VALUE, n.get("intB").asInteger().toInt());
		Assertions.assertEquals(Long.MIN_VALUE, n.get("longA").asInteger().toLong());
		Assertions.assertEquals(Long.MAX_VALUE, n.get("longB").asInteger().toLong());
		Assertions.assertEquals(Float.MIN_VALUE, n.get("floatA").asFloating().toFloat());
		Assertions.assertEquals(Float.MAX_VALUE, n.get("floatB").asFloating().toFloat());
		Assertions.assertEquals(Double.MIN_VALUE, n.get("doubleA").asFloating().toDouble());
		Assertions.assertEquals(Double.MAX_VALUE, n.get("doubleB").asFloating().toDouble());
		Assertions.assertThrows(JSONSerializer.JsonUseException.class, () -> n.get("longA").asInteger().toInt());
		Assertions.assertThrows(JSONSerializer.JsonUseException.class, () -> n.get("longB").asInteger().toInt());
		Assertions.assertEquals(JSONSerializer.JSON_NULL, n.get("null"));

		Assertions.assertEquals("value", n.get("object").asObject().get("string").asString().value());

		JSONSerializer.ArrayNode array = n.get("array").asArray();
		Assertions.assertEquals(6, array.size());
		Assertions.assertEquals(JSONSerializer.JSON_TRUE, array.get(0));
		Assertions.assertEquals(10, array.get(1).asNumeric().toInt());
		Assertions.assertEquals(1.0, array.get(2).asNumeric().toFloat());
		Assertions.assertEquals(JSONSerializer.JSON_NULL, array.get(3));
		Assertions.assertEquals(true, array.get(4).isObject());
		Assertions.assertEquals(true, array.get(5).isArray());

		JSONSerializer.ObjectNode n2 = array.get(4).asObject();
		Assertions.assertEquals("value", n2.get("child").asString().value());
		Assertions.assertEquals("\"value\"", n2.get("child").asString().toString());

		JSONSerializer.ArrayNode a2 = array.get(5).asArray();
		Assertions.assertEquals(1, a2.get(0).asNumeric().toInt());
	}

	@Test
	public void testEmpty() throws JSONSerializer.JsonParseException {
		Assertions.assertThrows(JSONSerializer.JsonParseException.class, () -> parseAny(""));
	}

	@Test
	public void testMalformedAny1() throws JSONSerializer.JsonParseException {
		Assertions.assertThrows(JSONSerializer.JsonParseException.class, () -> parseAny("{"));
	}

	@Test
	public void testMalformedAny2() throws JSONSerializer.JsonParseException {
		Assertions.assertThrows(JSONSerializer.JsonParseException.class, () -> parseAny("}"));
	}

	@Test
	public void testMalformedAny3() throws JSONSerializer.JsonParseException {
		Assertions.assertThrows(JSONSerializer.JsonParseException.class, () -> {
			try {
				parseAny("{\"test\"}");
			} catch (JSONSerializer.JsonParseException ex) {
				LogManager.getLogger(JSONSerializer_Test.class).debug(ex);
				throw ex;
			}
		});
	}
}
