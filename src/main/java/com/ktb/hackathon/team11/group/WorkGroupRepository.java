package com.ktb.hackathon.team11.group;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface WorkGroupRepository extends JpaRepository<WorkGroup,Long> { Optional<WorkGroup> findByInviteCode(String code); boolean existsByInviteCode(String code); }
