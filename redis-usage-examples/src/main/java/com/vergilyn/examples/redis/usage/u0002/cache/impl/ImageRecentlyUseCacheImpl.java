package com.vergilyn.examples.redis.usage.u0002.cache.impl;

import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.vergilyn.examples.commons.domain.Tuple;
import com.vergilyn.examples.redis.usage.u0002.cache.RecentlyUseCache;
import com.vergilyn.examples.redis.usage.u0002.entity.SourceImageEntity;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.stereotype.Component;

import static com.vergilyn.examples.redis.usage.u0002.cache.RecentlyUseCache.SourceTypeEnum.IMAGE;

@Slf4j
@Component
public class ImageRecentlyUseCacheImpl extends AbstractRecentlyUseCache<SourceImageEntity> {
	public static final Map<Integer, SourceImageEntity> DATASOURCE = Maps.newLinkedHashMap();
	public static final Integer[] INVALID = {19, 30, 31, 32, 33};
	static {
		SourceImageEntity temp;
		for (int i = 10; i <= 34; i ++){
			temp = new SourceImageEntity(i);
			temp.setDeleted(ArrayUtils.contains(INVALID, i));

			DATASOURCE.put(i, temp);
		}
	}

	public ImageRecentlyUseCacheImpl() {
		super();
	}

	public ImageRecentlyUseCacheImpl(long maxSize, long expiredSeconds) {
		super(maxSize, expiredSeconds);
	}

	@Override
	protected RecentlyUseCache.SourceTypeEnum getSourceType() {
		return IMAGE;
	}

	@Override
	protected List<SourceImageEntity> listByIds(List<Integer> ids) {
		List<SourceImageEntity> result = Lists.newArrayListWithCapacity(ids.size());

		/* 例如 mysql
		 *   SELECT * FROM tb_table WHERE id IN (4, 3, 5);
		 *   最终返回的数据顺序是`3, 4, 5`。
		 *
		 *   可以通过以下sql按IN顺序返回（但个人选择用java代码重新排序）
		 *   SELECT * FROM tb_table WHERE id IN (4, 3, 5) ORDER BY FIELD(`id`, 4, 3, 5)
		 */
		DATASOURCE.forEach((key, value) -> {
			if (ids.contains(key)) {
				result.add(value);
			}
		});

		return result;
	}

	@Override
	protected Tuple<List<SourceImageEntity>, List<SourceImageEntity>> filterEntities(List<Integer> expectedIds,
			List<SourceImageEntity> result) {
		Tuple<List<SourceImageEntity>, List<SourceImageEntity>> tuple = splitNormalDeleted(expectedIds, result, invalidId -> {
			SourceImageEntity sourceImageEntity = new SourceImageEntity(invalidId);
			sourceImageEntity.setDeleted(true);
			return sourceImageEntity;
		});

		return tuple;
	}
}