package com.firedemo.edumind.auth.authorization;

import com.firedemo.edumind.classroom.ClassInfo;
import com.firedemo.edumind.knowledge.DirectoryNode;
import com.firedemo.edumind.knowledge.Document;
import com.firedemo.edumind.live.Interaction;
import com.firedemo.edumind.teaching.TeachingCalendar;
import com.firedemo.edumind.classroom.ClassInfoMapper;
import com.firedemo.edumind.live.ClassroomSessionMapper;
import com.firedemo.edumind.classroom.CourseMapper;
import com.firedemo.edumind.knowledge.DirectoryNodeMapper;
import com.firedemo.edumind.knowledge.DocumentMapper;
import com.firedemo.edumind.homework.HomeworkTaskMapper;
import com.firedemo.edumind.live.InteractionMapper;
import com.firedemo.edumind.teaching.PreviewTaskMapper;
import com.firedemo.edumind.knowledge.SharedKbMapper;
import com.firedemo.edumind.knowledge.SharedKbMemberMapper;
import com.firedemo.edumind.homework.SubmissionMapper;
import com.firedemo.edumind.knowledge.TeacherKnowledgeMapper;
import com.firedemo.edumind.teaching.TeachingCalendarMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OwnershipGuardResourceAccessTest {

    @Mock private ClassInfoMapper classInfoMapper;
    @Mock private CourseMapper courseMapper;
    @Mock private HomeworkTaskMapper taskMapper;
    @Mock private DocumentMapper documentMapper;
    @Mock private DirectoryNodeMapper directoryNodeMapper;
    @Mock private SharedKbMapper sharedKbMapper;
    @Mock private SubmissionMapper submissionMapper;
    @Mock private TeacherKnowledgeMapper teacherKnowledgeMapper;
    @Mock private ClassroomSessionMapper sessionMapper;
    @Mock private PreviewTaskMapper previewTaskMapper;
    @Mock private SharedKbMemberMapper sharedKbMemberMapper;
    @Mock private TeachingCalendarMapper teachingCalendarMapper;

    @InjectMocks private OwnershipGuard guard;

    @BeforeEach
    void logInAsTeacher() {
        var auth = new UsernamePasswordAuthenticationToken(
                "teacher", null, List.of(new SimpleGrantedAuthority("ROLE_TEACHER")));
        auth.setDetails(100L);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void documentOwnershipUsesPublicDocumentIdInsteadOfDatabasePrimaryKey() {
        Document document = document("doc-public-id", 100L, null);
        when(documentMapper.selectByDocId("doc-public-id")).thenReturn(document);

        assertThat(guard.isDocumentOwner("doc-public-id")).isTrue();

        verify(documentMapper).selectByDocId("doc-public-id");
    }

    @Test
    void knowledgeBaseMemberCanReadSharedDocument() {
        when(documentMapper.selectByDocId("shared-doc"))
                .thenReturn(document("shared-doc", 200L, 9L));
        when(sharedKbMemberMapper.selectCount(any())).thenReturn(1L);

        assertThat(guard.canReadDocument(100L, "shared-doc")).isTrue();
        assertThat(guard.canWriteDocument(100L, "shared-doc")).isTrue();
    }

    @Test
    void nonMemberCannotReadSharedDocument() {
        when(documentMapper.selectByDocId("shared-doc"))
                .thenReturn(document("shared-doc", 200L, 9L));
        when(sharedKbMemberMapper.selectCount(any())).thenReturn(0L);

        assertThat(guard.canReadDocument(100L, "shared-doc")).isFalse();
        assertThat(guard.canWriteDocument(100L, "shared-doc")).isFalse();
    }

    @Test
    void publiclySharedPrivateDocumentIsReadOnlyForOtherTeachers() {
        when(documentMapper.selectByDocId("public-doc"))
                .thenReturn(document("public-doc", 200L, null));
        when(directoryNodeMapper.selectCount(any())).thenReturn(1L);

        assertThat(guard.canReadDocument(100L, "public-doc")).isTrue();
        assertThat(guard.canWriteDocument(100L, "public-doc")).isFalse();
    }

    @Test
    void sharedDirectoryAccessRequiresKnowledgeBaseMembership() {
        DirectoryNode node = new DirectoryNode();
        node.setId(5L);
        node.setUserId(200L);
        node.setKbId(9L);
        when(directoryNodeMapper.selectById(5L)).thenReturn(node);
        when(sharedKbMemberMapper.selectCount(any())).thenReturn(1L);

        assertThat(guard.canAccessDirectoryNode(100L, 5L)).isTrue();
    }

    @Test
    void anotherTeacherCannotDeleteTeachingCalendarPlan() {
        TeachingCalendar plan = new TeachingCalendar();
        plan.setId(7L);
        plan.setTeacherId(200L);
        when(teachingCalendarMapper.selectById(7L)).thenReturn(plan);

        assertThat(guard.isTeachingCalendarOwner(7L)).isFalse();
    }

    private Document document(String docId, Long ownerId, Long kbId) {
        Document document = new Document();
        document.setDocId(docId);
        document.setUserId(ownerId);
        document.setKbId(kbId);
        return document;
    }
}
