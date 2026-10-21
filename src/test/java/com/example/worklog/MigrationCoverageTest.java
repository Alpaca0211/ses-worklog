package com.example.worklog;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.worklog.domain.SanitizeStatus;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * マイグレーションの書き漏れを検出する。
 *
 * <p>テストは H2 にエンティティからスキーマを作らせるため、Flyway の SQL を一切通らない。
 * つまり「列挙に値を足したのにマイグレーションを書いていない」状態でもテストは緑になり、
 * PostgreSQL で初めて CHECK 制約違反として落ちる。実際にそれを踏んだので、
 * 列挙と SQL の対応だけは静的に突き合わせる。
 *
 * <p>Spring を起動しないので、テスト全体の実行時間には影響しない。
 */
class MigrationCoverageTest {

    private static String allMigrations() {
        Path dir = Path.of("src/main/resources/db/migration");
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(p -> p.toString().endsWith(".sql"))
                    .map(p -> {
                        try {
                            return Files.readString(p, StandardCharsets.UTF_8);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    })
                    .collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void 秘匿化ステータスの値はすべてマイグレーションに現れる() {
        String sql = allMigrations();

        for (SanitizeStatus status : SanitizeStatus.values()) {
            assertThat(sql)
                    .as("%s が CHECK 制約に含まれていません。"
                            + "列挙に値を足したらマイグレーションを追加してください", status.name())
                    .contains("'" + status.name() + "'");
        }
    }
}
