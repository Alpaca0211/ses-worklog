-- 短い記録は抽象化を通さない（LLM が書かれていない内容を補うため）。
-- その状態を未処理と区別するため SanitizeStatus に値を足した。
-- status は文字列で保持しており、許可値は CHECK 制約で縛られている。
alter table daily_log drop constraint if exists daily_log_status_check;
alter table daily_log add constraint daily_log_status_check
    check (status in ('MASKED_ONLY', 'ABSTRACTED', 'ABSTRACTED_REMASKED', 'ABSTRACTION_SKIPPED'));
