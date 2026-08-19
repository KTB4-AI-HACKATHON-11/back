package com.ktb.hackathon.team11.member;

import com.ktb.hackathon.team11.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 30)
    private String nickname;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private MemberRole role;

    public Member(String nickname, MemberRole role) {
        this.nickname = nickname.strip();
        this.role = role;
    }
}
