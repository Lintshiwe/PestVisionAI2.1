package com.pestvisionai.backend.repository;

import com.pestvisionai.backend.model.SprayEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SprayEventRepository extends JpaRepository<SprayEvent, Long> {

	List<SprayEvent> findTop50ByOrderByTriggeredAtDesc();
}
