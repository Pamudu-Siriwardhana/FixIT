package com.serviceportal.controller.api;

import com.serviceportal.dto.Dtos.DocumentView;
import com.serviceportal.model.VerificationDocument;
import com.serviceportal.service.CurrentUser;
import com.serviceportal.service.DocumentService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.List;

/** Verification documents: users upload their own, admins open anyone's. */
@RestController
@RequestMapping("/api")
public class DocumentApiController {

    private final CurrentUser currentUser;
    private final DocumentService documents;

    public DocumentApiController(CurrentUser currentUser, DocumentService documents) {
        this.currentUser = currentUser;
        this.documents = documents;
    }

    @PostMapping(value = "/me/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentView upload(@RequestParam("file") MultipartFile file,
                               @RequestParam(value = "label", required = false) String label, Principal principal) {
        return documents.upload(currentUser.of(principal), label, file);
    }

    @GetMapping("/me/documents")
    public List<DocumentView> mine(Principal principal) {
        return documents.list(currentUser.of(principal));
    }

    @DeleteMapping("/me/documents/{id}")
    public void delete(@PathVariable Long id, Principal principal) {
        documents.delete(currentUser.of(principal), id);
    }

    @GetMapping("/documents/{id}/file")
    public ResponseEntity<byte[]> file(@PathVariable Long id, Principal principal) {
        VerificationDocument d = documents.open(id, currentUser.of(principal));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(d.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(d.getFileName()).build().toString())
                .body(d.getData());
    }

    @GetMapping("/admin/users/{userId}/documents")
    public List<DocumentView> forUser(@PathVariable Long userId) {
        return documents.listFor(userId);
    }
}
