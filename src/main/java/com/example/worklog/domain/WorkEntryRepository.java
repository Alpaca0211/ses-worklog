package com.example.worklog.domain;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WorkEntryRepository extends JpaRepository<WorkEntry, Long> {

    List<WorkEntry> findByWorkDateBetweenOrderByWorkDateAscProjectDisplayOrderAscIdAsc(LocalDate from, LocalDate to);

    List<WorkEntry> findByWorkDateOrderByIdAsc(LocalDate workDate);

    /**
     * 過去に使った施策名の候補。入力欄のサジェストに使う。
     * 毎回打ち直させると「リリース」と「リリリース」のような表記ゆれが生まれ、
     * 前日分をコピーする運用ではそれがそのまま伝播するため。
     */
    @Query("select distinct w.workstream from WorkEntry w "
            + "where w.workstream is not null and w.workstream <> '' order by w.workstream")
    List<String> findDistinctWorkstreams();
}
