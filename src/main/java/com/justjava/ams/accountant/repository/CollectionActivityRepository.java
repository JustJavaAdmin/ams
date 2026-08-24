package com.justjava.ams.accountant.repository;

import com.justjava.ams.accountant.entity.CollectionActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CollectionActivityRepository extends JpaRepository<CollectionActivity, Long> {
    List<CollectionActivity> findByCollectionCaseIdOrderByCreatedAtDescIdDesc(Long collectionCaseId);
}
