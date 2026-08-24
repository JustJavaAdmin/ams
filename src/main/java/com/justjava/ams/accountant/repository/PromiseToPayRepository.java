package com.justjava.ams.accountant.repository;

import com.justjava.ams.accountant.entity.PromiseToPay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface PromiseToPayRepository extends JpaRepository<PromiseToPay, Long> {
    List<PromiseToPay> findByCollectionCaseIdOrderByPromisedDateDescIdDesc(Long collectionCaseId);
    List<PromiseToPay> findByCollectionCaseIdAndStatusIn(Long collectionCaseId, Collection<PromiseToPay.PromiseStatus> statuses);
}
