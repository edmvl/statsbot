-- Нельзя оставить отзыв самому себе.
--
-- Удаляем уже накопленные self-отзывы и запрещаем их на уровне БД,
-- чтобы правило нельзя было обойти в обход приложения.
delete from rating
where user_id = review_user_id;

alter table rating drop constraint if exists rating_no_self_review;
alter table rating
    add constraint rating_no_self_review
    check (user_id is null or review_user_id is null or user_id <> review_user_id);
