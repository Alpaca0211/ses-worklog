package com.example.worklog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.worklog.career.CareerService;
import com.example.worklog.domain.Project;
import com.example.worklog.domain.ProjectRepository;
import com.example.worklog.domain.Technology;
import com.example.worklog.domain.TechnologyCategory;
import com.example.worklog.domain.TechnologyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 案件・技術マスタの追加と切り替え。 */
class CareerMasterTest extends SanitizeTestBase {

    @Autowired
    CareerService service;

    @Autowired
    ProjectRepository projects;

    @Autowired
    TechnologyRepository technologies;

    @BeforeEach
    void setUp() {
        projects.deleteAll();
        technologies.deleteAll();
    }

    @Test
    void 技術を区分付きで追加できる() {
        Technology t = service.addTechnology("Kotlin", TechnologyCategory.LANGUAGE_FRAMEWORK);

        assertThat(t.getId()).isNotNull();
        assertThat(t.getCategory()).isEqualTo(TechnologyCategory.LANGUAGE_FRAMEWORK);
        assertThat(t.isActive()).isTrue();
    }

    @Test
    void 同名の技術を追加しても増えない() {
        service.addTechnology("Kotlin", TechnologyCategory.LANGUAGE_FRAMEWORK);
        // 大文字小文字が違っても同じ技術として扱う。表記ゆれで二重登録しないため
        service.addTechnology("kotlin", TechnologyCategory.OS_TOOL);

        assertThat(technologies.count()).isEqualTo(1);
    }

    @Test
    void 技術名が空なら追加しない() {
        assertThatThrownBy(() -> service.addTechnology("  ", TechnologyCategory.OS_TOOL))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(technologies.count()).isZero();
    }

    @Test
    void 技術の有効無効を切り替えられる() {
        Technology t = service.addTechnology("Kotlin", TechnologyCategory.LANGUAGE_FRAMEWORK);

        service.toggleTechnology(t.getId());
        assertThat(technologies.findById(t.getId()).orElseThrow().isActive()).isFalse();

        service.toggleTechnology(t.getId());
        assertThat(technologies.findById(t.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    void 案件を追加できる() {
        Project p = service.addProject("新規案件");

        assertThat(p.getId()).isNotNull();
        assertThat(p.isActive()).isTrue();
    }

    @Test
    void 同名の案件を追加しても増えない() {
        service.addProject("新規案件");
        service.addProject("新規案件");

        assertThat(projects.count()).isEqualTo(1);
    }

    @Test
    void 案件名が空なら追加しない() {
        assertThatThrownBy(() -> service.addProject(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(projects.count()).isZero();
    }
}
