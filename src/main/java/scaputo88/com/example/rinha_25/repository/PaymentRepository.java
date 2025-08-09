package scaputo88.com.example.rinha_25.repository;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import scaputo88.com.example.rinha_25.model.PaymentEntity;

import java.util.UUID;

@Repository
@Profile("dev")
public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {
}

