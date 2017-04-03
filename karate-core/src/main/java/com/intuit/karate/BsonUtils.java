package com.intuit.karate;


import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import org.bson.*;
import org.bson.codecs.BsonDocumentCodec;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.bson.io.BasicOutputBuffer;

import java.nio.ByteBuffer;
import java.util.Date;

/**
 *
 * @author ambertests
 */
public class BsonUtils {
    private static Codec<BsonDocument> DOC_CODEC = new BsonDocumentCodec();

    private BsonUtils(){}

    public static byte[] toByteArray(BsonDocument bsonDocument){
        BasicOutputBuffer outputBuffer = new BasicOutputBuffer();
        BsonBinaryWriter writer = new BsonBinaryWriter(outputBuffer);
        DOC_CODEC.encode(writer, bsonDocument, EncoderContext.builder().isEncodingCollectibleDocument(true).build());
        return outputBuffer.toByteArray();
    }

    public static BsonDocument fromByteArray(byte[] bytes){
        BsonDocument bsonDoc = null;
        if (bytes != null && bytes.length > 0){
            BsonBinaryReader bsonReader = new BsonBinaryReader(ByteBuffer.wrap(bytes));
            try {
                bsonDoc = DOC_CODEC.decode(bsonReader, DecoderContext.builder().build());
            } catch (BsonSerializationException e) {
                //e.printStackTrace();
            }
        }
        return bsonDoc;
    }
	public static JSONObject bsonToJson(BsonDocument bson) {
		JSONObject json = new JSONObject();
		for (String key : bson.keySet()) {
			BsonValue val = bson.get(key);
			if (val.isArray()) {
				json.put(key, bsonListToJsonArray(val.asArray()));
			}else if (val.isDocument()) {
				json.put(key, bsonToJson(val.asDocument()));
			}else {
				json.put(key, getObjectFromBsonValue(val));
			}

		}
		return json;
	}

	private static Object getObjectFromBsonValue(BsonValue val){
		Object obj;
		if (val.isString()){
			obj = val.asString().getValue();
		}else if (val.isInt32()) {
			obj = val.asInt32().intValue();
		}else if (val.isBoolean()){
			obj = val.asBoolean().getValue();
		}else if (val.isDateTime()){
			obj = val.asDateTime().getValue();
		}else if (val.isDouble()){
			obj = val.asDouble().getValue();
		}else if (val.isInt64()){
			obj = val.asInt64().longValue();
		}else if (val.isBinary()){
            obj = val.asBinary().getData();
        }else if (val.isNull()){
			obj = null;
		}else if (val.isTimestamp()){
			obj = val.asTimestamp().getTime();
		}else{
			obj = val.toString();
		}
		return obj;
	}

	private static JSONArray bsonListToJsonArray(BsonArray bsonList){
		JSONArray jsonArr = new JSONArray();
		for (BsonValue val : bsonList) {
			if (val.isArray()) {
				jsonArr.add(bsonListToJsonArray(val.asArray()));
			} else if (val.isDocument()) {
				jsonArr.add(bsonToJson(val.asDocument()));
			} else if (val.isBinary()) {
				jsonArr.add(new String(val.asBinary().getData()));
			} else {
				jsonArr.add(getObjectFromBsonValue(val));
			}
		}
		return jsonArr;
	}

	private static BsonValue getBsonValueFromObject(Object val){
		BsonValue obj;
		if(val == null){
			obj = new BsonNull();
		}else if (val instanceof String){
			obj = new BsonString(val.toString());
		}else if (val instanceof Integer) {
			obj = new BsonInt32((Integer)val);
		}else if (val instanceof byte[]) {
			obj = new BsonBinary((byte[])val);
		}else if (val instanceof Boolean){
			obj = new BsonBoolean((Boolean) val);
		}else if (val instanceof Date){
			obj = new BsonDateTime(((Date)val).getTime());
		}else if (val instanceof Double){
			obj = new BsonDouble((Double)val);
		}else if (val instanceof Long){
			obj = new BsonInt64((Long)val);
		}else{
			obj = new BsonString(val.toString());
		}
		return obj;
	}

	public static BsonDocument jsonToBson(JSONObject json)  {
		BsonDocument bson = new BsonDocument();
		for(String key:json.keySet()) {
			Object val = json.get(key);
			if (val instanceof JSONArray) {
				bson.put(key, jsonArrayToBsonList((JSONArray) val));
			} else if (val instanceof JSONObject) {
				bson.put(key, jsonToBson((JSONObject) val));
			} else {
				bson.put(key, getBsonValueFromObject(val));
			}
		}
		return bson;
	}

	private static BsonArray jsonArrayToBsonList(JSONArray jArr)  {
		BsonArray arr = new BsonArray();
		for (Object val:jArr) {
			if (val instanceof JSONArray) {
				arr.add(jsonArrayToBsonList((JSONArray) val));
			} else if (val instanceof JSONObject) {
				arr.add(jsonToBson((JSONObject) val));
			} else {
				arr.add(getBsonValueFromObject(val));
			}
		}
		return arr;
	}

}
