package com.serviceportal.service;

import com.serviceportal.dto.Dtos.DocumentView;
import com.serviceportal.model.*;
import com.serviceportal.repository.UserRepository;
import com.serviceportal.repository.VerificationDocumentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/** Verification documents (NIC photo, certificates): PNG, JPEG or PDF up to 5 MB, at most 10 per user. */
@Service
@Transactional
public class DocumentService {

    static final long MAX_BYTES = 5L * 1024 * 1024;
    static final int MAX_PER_USER = 10;
    static final Set<String> TYPES = Set.of("image/png", "image/jpeg", "application/pdf");

    private final VerificationDocumentRepository documents;
    private final UserRepository users;

    public DocumentService(VerificationDocumentRepository documents, UserRepository users) {
        this.documents = documents;
        this.users = users;
    }

    static DocumentView view(VerificationDocument d) {
        return new DocumentView(d.getId(), d.getLabel(), d.getFileName(), d.getContentType(), d.getSize(), d.getUploadedAt());
    }

    public DocumentView upload(UserAccount user, String label, MultipartFile file) {
        if (user.getRole() == Role.ADMIN) throw ApiException.badRequest("Admin accounts do not need verification documents");
        if (file == null || file.isEmpty()) throw ApiException.badRequest("Choose a file to upload");
        if (file.getSize() > MAX_BYTES) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "File too large (max 5 MB)");
        String ct = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!TYPES.contains(ct)) throw ApiException.badRequest("Document must be a PNG, JPEG or PDF file");
        if (documents.countByUser(user) >= MAX_PER_USER) throw ApiException.conflict("You can keep at most " + MAX_PER_USER + " documents. Delete one first.");

        VerificationDocument d = new VerificationDocument();
        d.setUser(user);
        d.setLabel(label == null || label.isBlank() ? "Document" : label.trim().substring(0, Math.min(label.trim().length(), 100)));
        d.setFileName(file.getOriginalFilename() == null ? "document" : file.getOriginalFilename().replaceAll("[^A-Za-z0-9._-]", "_"));
        d.setContentType(ct);
        d.setSize(file.getSize());
        try {
            d.setData(file.getBytes());
        } catch (IOException e) {
            throw ApiException.badRequest("Could not read the uploaded file");
        }
        if (user.getStatus() == VerificationStatus.REJECTED) {   // new evidence puts a rejected profile back in the queue
            user.setStatus(VerificationStatus.PENDING);
            user.setRejectionReason(null);
            users.save(user);
        }
        return view(documents.save(d));
    }

    @Transactional(readOnly = true)
    public List<DocumentView> list(UserAccount user) {
        return documents.findByUserOrderByUploadedAtDescIdDesc(user).stream().map(DocumentService::view).toList();
    }

    /** Admin: documents of any user. */
    @Transactional(readOnly = true)
    public List<DocumentView> listFor(Long userId) {
        UserAccount u = users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
        return list(u);
    }

    public void delete(UserAccount user, Long id) {
        VerificationDocument d = documents.findById(id).orElseThrow(() -> ApiException.notFound("Document not found"));
        if (!d.getUser().getId().equals(user.getId())) throw ApiException.forbidden("Not your document");
        if (user.isVerified()) throw ApiException.conflict("Documents of a verified profile cannot be deleted");
        documents.delete(d);
    }

    /** The owner or an admin may open a document. */
    @Transactional(readOnly = true)
    public VerificationDocument open(Long id, UserAccount viewer) {
        VerificationDocument d = documents.findById(id).orElseThrow(() -> ApiException.notFound("Document not found"));
        if (viewer.getRole() != Role.ADMIN && !d.getUser().getId().equals(viewer.getId())) {
            throw ApiException.forbidden("Not your document");
        }
        return d;
    }
}
