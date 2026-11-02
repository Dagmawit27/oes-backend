package com.oes.onlineexamsystem.examination.repository;

import com.oes.onlineexamsystem.examination.entity.Examination;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExaminationRepository extends JpaRepository<Examination, Long>, JpaSpecificationExecutor<Examination> {

    List<Examination> findByCourseOfferingId(Long courseOfferingId);

    List<Examination> findByCreatedById(Long createdById);

    boolean existsByCourseOfferingIdAndTitleIgnoreCase(Long offeringId, String title);

    boolean existsByCourseOfferingIdAndTitleIgnoreCaseAndIdNot(Long offeringId, String title, Long id);
}
