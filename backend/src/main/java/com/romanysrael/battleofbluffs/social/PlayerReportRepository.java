package com.romanysrael.battleofbluffs.social;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerReportRepository extends JpaRepository<PlayerReportEntity, UUID> {
}
