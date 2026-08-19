package com.ktb.hackathon.team11.group;

import com.ktb.hackathon.team11.global.common.BaseEntity;
import com.ktb.hackathon.team11.member.Member;
import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name="work_groups")
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class WorkGroup extends BaseEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false, length=80) private String name;
    @Column(nullable=false, unique=true, length=6) private String inviteCode;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) private Member owner;
    public WorkGroup(String name, String inviteCode, Member owner) { this.name=name.strip(); this.inviteCode=inviteCode; this.owner=owner; }
}
