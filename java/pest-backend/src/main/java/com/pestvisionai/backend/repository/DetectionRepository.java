package com.pestvisionai.backend.repository;

import com.pestvisionai.backend.model.Detection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DetectionRepository extends JpaRepository<Detection, Long> {

	List<Detection> findTop50ByOrderByDetectedAtDesc();
}
