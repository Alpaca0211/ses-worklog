package com.example.worklog;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * テストは LLM 無効・インメモリDB で動かす。
 * 秘匿化の中核は決定論的な辞書マスキングなので、LLM 無しでも本質はすべて検証できる。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:worklogtest;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "worklog.crypto.key=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=",
        "worklog.llm.enabled=false"
})
@ActiveProfiles("test")
public abstract class SanitizeTestBase {
}
