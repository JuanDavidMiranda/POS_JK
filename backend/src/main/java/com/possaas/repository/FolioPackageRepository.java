package com.possaas.repository;

import com.possaas.domain.FolioPackage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FolioPackageRepository extends JpaRepository<FolioPackage, String> {
    List<FolioPackage> findAllByOrderByFoliosCountAsc();
}
