package com.finflow.troubleshooting.module14;

import com.finflow.troubleshooting.module14.cache.BoundedCacheService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Module14Application.class)
public class BoundedCacheEvictionTest {

    @Autowired
    private BoundedCacheService cacheService;

    @Test
    void testBoundedCacheEnforcesMaxSizeAndEvictsOldestEntries() {
        int maxCapacity = cacheService.getMaxCacheEntries(); // 5

        // Insert 8 entries (keys 1 to 8)
        for (int i = 1; i <= 8; i++) {
            cacheService.putBounded("KEY-" + i, "VAL-" + i);
        }

        // Cache size must not exceed 5
        assertThat(cacheService.getBoundedSize()).isEqualTo(maxCapacity);

        // Oldest entries (KEY-1, KEY-2, KEY-3) should have been evicted
        assertThat(cacheService.getBounded("KEY-1")).isNull();
        assertThat(cacheService.getBounded("KEY-2")).isNull();
        assertThat(cacheService.getBounded("KEY-3")).isNull();

        // Recent entries (KEY-4 through KEY-8) must exist
        assertThat(cacheService.getBounded("KEY-8")).isEqualTo("VAL-8");
        assertThat(cacheService.getBounded("KEY-7")).isEqualTo("VAL-7");
    }
}
