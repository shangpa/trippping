package com.study.board.repository;

import com.study.board.entity.Comment;
import com.study.board.user.SiteUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface  CommentRepository extends JpaRepository<Comment, Integer> {
    List<Comment> findByAuthor(SiteUser author);
}
