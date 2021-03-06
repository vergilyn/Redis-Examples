package com.vergilyn.examples.jedis.multi;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.vergilyn.examples.commons.redis.JedisClientFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;

public class JedisPipelineMultiGetTests {

	private static final String KEY_STRING = "STR:";

	private static final String KEY_HASH = "HASH:";

	private static final String KEY_LIST = "LIST:";

	private static final String KEY_SET = "SET:";

	private static final String KEY_SORT = "SORT:";

	@BeforeEach
	public void init() {
		Jedis jedis = JedisClientFactory.getInstance().jedis();
		Pipeline pipelined = jedis.pipelined();
		// String
		for (int i = 0; i < 10; i++) {
			pipelined.set(KEY_STRING + i, "str_val_" + i);
		}

		pipelined.sync();
		pipelined.clear();

		// HASH
		for (int i = 0; i < 10; i++) {
			Map<String, String> map = Maps.newHashMap();
			String key = KEY_HASH + i;
			map.put("field-01", key + "_val_01");
			map.put("field-02", key + "_val_02");
			map.put("field-03", key + "_val_03");
			pipelined.hmset(key, map);
		}

		pipelined.sync();
		pipelined.clear();

		// list
		for (int i = 0; i < 10; i++) {
			String key = KEY_LIST + i;
			String[] vals = { key + "_01", key + "_02", key + "_03", key + "_04" };
			pipelined.lpush(key, vals);
		}
		pipelined.sync();
		pipelined.clear();

		// SET
		for (int i = 0; i < 10; i++) {
			String key = KEY_SET + i;
			String[] vals = { key + "_01", key + "_02", key + "_03", key + "_04" };
			pipelined.sadd(key, vals);
		}
		pipelined.sync();
		pipelined.clear();

		// SORT
		for (int i = 0; i < 10; i++) {
			Map<String, Double> map = Maps.newHashMap();
			String key = KEY_SORT + i;

			map.put(key + "val_01_S4", 4D);
			map.put(key + "val_02_S1", 1D);
			map.put(key + "val_03_S3", 3D);
			map.put(key + "val_04_S2", 2D);
			pipelined.zadd(key, map);
		}
		pipelined.sync();
		pipelined.clear();

		pipelined.close();
		jedis.close();
	}

	@Test
	public void mgetSort() {
		Map<String, List<String>> allList = JedisPipelineMultiGet
				.mgetSort(new String[] { KEY_SORT + 1, KEY_SORT + 2, KEY_SORT + "X" },
						0, -1,
						String.class,
						key -> {
							List<String> xx = Lists.newArrayList();
							xx.add("xx_01");
							xx.add("xx_02");
							xx.add("xx_03");
							Collections.reverse(xx); // 用的是lrange, 所以注意需要的LIST的顺序
							return xx;
						}
				);
		System.out.println(JSON.toJSONString(allList, SerializerFeature.WriteNullStringAsEmpty));
	}

	@Test
	public void mgetSet() {
		Map<String, List<String>> allList = JedisPipelineMultiGet
				.mgetSet(new String[] { KEY_SET + 1, KEY_SET + 2, KEY_SET + "X" },
						String.class,
						key -> {
							List<String> xx = Lists.newArrayList();
							xx.add("xx_01");
							xx.add("xx_02");
							xx.add("xx_03");
							Collections.reverse(xx); // 用的是lrange, 所以注意需要的LIST的顺序
							return xx;
						}
				);
		System.out.println(JSON.toJSONString(allList, SerializerFeature.WriteNullStringAsEmpty));
	}

	@Test
	public void mgetListLrange() {
		Map<String, List<String>> allList = JedisPipelineMultiGet
				.mgetListLrange(new String[] { KEY_LIST + 1, KEY_LIST + 2, KEY_LIST + "X" },
						0, 1,
						String.class,
						key -> {
							List<String> xx = Lists.newArrayList();
							xx.add("xx_01");
							xx.add("xx_02");
							xx.add("xx_03");
							Collections.reverse(xx); // 用的是lrange, 所以注意需要的LIST的顺序
							return xx;
						}
				);
		System.out.println(JSON.toJSONString(allList, SerializerFeature.WriteNullStringAsEmpty));
	}

	@Test
	public void mgetFieldsHash() {
		Map<String, Map<String, String>> allHash = JedisPipelineMultiGet
				.mgetHash(
						new String[] { KEY_HASH + 1, KEY_HASH + 2, KEY_HASH + "X" },
						new String[] { "field-01", "field-03", "field-0x" },
						key -> {
							Map<String, String> map = new HashMap<>();
							map.put("xx", "1");
							map.put("CC", "2");
							return map;
						}
				);
		System.out.println(JSON.toJSONString(allHash, SerializerFeature.WriteNullStringAsEmpty));
	}

	@Test
	public void mgetAllHash() {
		Map<String, Map<String, String>> allHash = JedisPipelineMultiGet
				.mgetHash(
						new String[] { KEY_HASH + 1, KEY_HASH + 2, KEY_HASH + "X" },
						null,
						key -> {
							Map<String, String> map = new HashMap<>();
							map.put("xx", "1");
							map.put("CC", "2");
							return map;
						}
				);
		System.out.println(JSON.toJSONString(allHash, SerializerFeature.WriteNullStringAsEmpty));
	}

	@Test
	public void mgetString() {
		Map<String, String> allHash = JedisPipelineMultiGet
				.mgetString(
						new String[] { KEY_STRING + "1", KEY_STRING + "2", KEY_STRING + "X" },
						String.class, key -> "2333"
				);

		System.out.println(JSON.toJSONString(allHash, SerializerFeature.WriteNullStringAsEmpty));
	}
}
