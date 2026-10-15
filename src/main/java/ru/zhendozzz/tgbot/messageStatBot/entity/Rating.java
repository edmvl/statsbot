package ru.zhendozzz.tgbot.messageStatBot.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.Size;

@Getter
@Setter
@Entity
@Table(name = "rating")
public class Rating {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Size(max = 255)
    @Column(name = "user_id")
    private String userId;

    @Size(max = 255)
    @Column(name = "review_user_id")
    private String reviewUserId;

    @Column(name = "stars")
    private Integer stars;

    @Size(max = 255)
    @Column(name = "comment")
    private String comment;

    public Rating(String userId, String reviewUserId, String rating, String comment) {
        this.reviewUserId = reviewUserId;
        this.userId = userId;
        this.stars = Integer.parseInt(rating);
        this.comment = comment;
    }

    public Rating() {

    }
}