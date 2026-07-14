package com.github.paicoding.forum.test.util;

import com.github.paicoding.forum.core.util.UrlSlugUtil;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * URL slug 工具测试。
 */
public class UrlSlugUtilTest {

    @Test
    public void shouldGenerateValidSlugForAnyInput() {
        assertValidSlug(UrlSlugUtil.generateSlug(null));
        assertValidSlug(UrlSlugUtil.generateSlug(""));
        assertValidSlug(UrlSlugUtil.generateSlug("   \t\n"));
        assertValidSlug(UrlSlugUtil.generateSlug("!!! 😀 @@@"));
        assertValidSlug(UrlSlugUtil.generateSlug("技术派 Spring Boot 教程"));
        assertValidSlug(UrlSlugUtil.generateSlug("123456"));
    }

    @Test
    public void shouldLimitSlugLength() {
        String slug = UrlSlugUtil.generateSlug(
                "this is an extremely long article title that should be truncated without leaving an invalid trailing dash "
                        + "with-more-content");

        assertValidSlug(slug);
        assertTrue(slug.length() <= 100);
        assertFalse(slug.endsWith("-"));
    }

    private void assertValidSlug(String slug) {
        assertNotNull(slug);
        assertTrue(slug.matches("^[a-z0-9-]+$"));
        assertTrue(slug.length() <= 100);
        assertFalse(slug.matches("^[0-9]+$"));
    }
}
