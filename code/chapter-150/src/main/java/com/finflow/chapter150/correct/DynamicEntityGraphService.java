package com.finflow.chapter150.correct;

import com.finflow.chapter150.domain.PaymentOrder;
import jakarta.persistence.EntityGraph;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class DynamicEntityGraphService {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<PaymentOrder> findOrdersWithDynamicGraph(String merchantId, Set<String> expandFields) {
        EntityGraph<PaymentOrder> entityGraph = entityManager.createEntityGraph(PaymentOrder.class);

        if (expandFields != null) {
            if (expandFields.contains("merchant")) {
                entityGraph.addAttributeNodes("merchantAccount");
            }
            if (expandFields.contains("items")) {
                entityGraph.addAttributeNodes("items");
            }
            if (expandFields.contains("auditLogs")) {
                entityGraph.addAttributeNodes("auditLogs");
            }
        }

        TypedQuery<PaymentOrder> query = entityManager.createQuery(
                "SELECT p FROM PaymentOrder p WHERE p.merchantId = :merchantId",
                PaymentOrder.class
        );
        query.setParameter("merchantId", merchantId);
        query.setHint("jakarta.persistence.fetchgraph", entityGraph);

        return query.getResultList();
    }
}
