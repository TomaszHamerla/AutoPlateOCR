package com.example.autoplateocr.repository;

import com.example.autoplateocr.model.LicensePlateRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LicensePlateRepository  extends JpaRepository<LicensePlateRecord, Long> {
}
