package com.dikoin.manuals.repositorios;

import com.dikoin.manuals.entidades.Role;
import com.dikoin.manuals.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByName(UserRole name);
}
