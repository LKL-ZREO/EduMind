package com.firedemo.demo.Service.ServiceImpl;

import com.firedemo.demo.Entity.SharedKb;
import com.firedemo.demo.Entity.SharedKbMember;
import com.firedemo.demo.mapper.SharedKbMapper;
import com.firedemo.demo.mapper.SharedKbMemberMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SharedKbServiceImpl — 共享知识库")
class SharedKbServiceImplTest {

    @Mock private SharedKbMapper sharedKbMapper;
    @Mock private SharedKbMemberMapper memberMapper;

    @InjectMocks
    private SharedKbServiceImpl service;

    private SharedKb sample;

    @BeforeEach
    void setUp() {
        sample = new SharedKb();
        sample.setId(1L);
        sample.setName("教研组知识库");
        sample.setOwnerId(100L);
        sample.setInviteToken("token123");
        sample.setInviteUseCount(0);
        sample.setInviteMaxUses(10);
        sample.setInviteExpiresAt(LocalDateTime.now().plusDays(30));
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("创建知识库 + 创建者自动加入为 owner")
        void shouldCreateAndAddOwner() {
            when(sharedKbMapper.insert(any(SharedKb.class))).thenReturn(1);
            when(memberMapper.insert(any(SharedKbMember.class))).thenReturn(1);

            SharedKb result = service.create(100L, "新知识库", "描述");

            assertThat(result.getName()).isEqualTo("新知识库");
            assertThat(result.getOwnerId()).isEqualTo(100L);
            assertThat(result.getInviteToken()).isNotEmpty();

            // 验证两次写入：知识库 + 成员
            verify(sharedKbMapper).insert(any(SharedKb.class));
            verify(memberMapper).insert(any(SharedKbMember.class));
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("删除知识库 → 级联删除成员")
        void shouldDeleteWithMembers() {
            when(sharedKbMapper.selectById(1L)).thenReturn(sample);
            when(memberMapper.delete(any())).thenReturn(1);

            service.delete(100L, 1L);

            verify(memberMapper).delete(any()); // 删除所有成员
            verify(sharedKbMapper).deleteById(eq(1L)); // 删除知识库
        }

        @Test
        @DisplayName("非创建者 → 抛异常")
        void shouldThrowWhenNotOwner() {
            when(sharedKbMapper.selectById(1L)).thenReturn(sample);

            assertThatThrownBy(() -> service.delete(999L, 1L))
                    .isInstanceOf(IllegalArgumentException.class);
            verify(sharedKbMapper, never()).deleteById(anyLong());
            verify(memberMapper, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("joinByToken")
    class JoinByToken {

        @Test
        @DisplayName("有效邀请 → 加入成功，使用次数 +1")
        void shouldJoinSuccessfully() {
            when(sharedKbMapper.selectByInviteToken("token123")).thenReturn(sample);
            when(memberMapper.selectCount(any())).thenReturn(0L); // 还不是成员
            when(memberMapper.insert(any(SharedKbMember.class))).thenReturn(1);
            when(sharedKbMapper.updateById(any(SharedKb.class))).thenReturn(1);

            service.joinByToken(200L, "token123");

            verify(memberMapper).insert(any(SharedKbMember.class));
            assertThat(sample.getInviteUseCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("邀请过期 → 抛异常")
        void shouldThrowWhenExpired() {
            sample.setInviteExpiresAt(LocalDateTime.now().minusDays(1));
            when(sharedKbMapper.selectByInviteToken("token123")).thenReturn(sample);

            assertThatThrownBy(() -> service.joinByToken(200L, "token123"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("过期");
        }

        @Test
        @DisplayName("已达最大使用次数 → 抛异常")
        void shouldThrowWhenMaxUses() {
            sample.setInviteMaxUses(5);
            sample.setInviteUseCount(5); // 已用满
            when(sharedKbMapper.selectByInviteToken("token123")).thenReturn(sample);

            assertThatThrownBy(() -> service.joinByToken(200L, "token123"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("最大使用次数");
        }

        @Test
        @DisplayName("已是成员 → 抛异常")
        void shouldThrowWhenAlreadyMember() {
            when(sharedKbMapper.selectByInviteToken("token123")).thenReturn(sample);
            when(memberMapper.selectCount(any())).thenReturn(1L);

            assertThatThrownBy(() -> service.joinByToken(200L, "token123"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("已是");
        }
    }

    @Nested
    @DisplayName("removeMember")
    class RemoveMember {

        @Test
        @DisplayName("创建者踢出成员 → 成功")
        void shouldRemoveByOwner() {
            when(sharedKbMapper.selectById(1L)).thenReturn(sample);
            when(memberMapper.delete(any())).thenReturn(1);

            service.removeMember(100L, 1L, 300L);

            verify(memberMapper).delete(any());
        }

        @Test
        @DisplayName("不能踢出创建者")
        void shouldThrowWhenRemoveOwner() {
            when(sharedKbMapper.selectById(1L)).thenReturn(sample);

            assertThatThrownBy(() -> service.removeMember(100L, 1L, 100L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("创建者");
        }
    }

    @Nested
    @DisplayName("changeRole")
    class ChangeRole {

        @Test
        @DisplayName("创建者修改成员角色 → 成功")
        void shouldChangeRole() {
            when(sharedKbMapper.selectById(1L)).thenReturn(sample);
            SharedKbMember member = new SharedKbMember();
            member.setKbId(1L);
            member.setUserId(300L);
            member.setRole("member");
            when(memberMapper.selectOne(any())).thenReturn(member);

            service.changeRole(100L, 1L, 300L, "admin");

            assertThat(member.getRole()).isEqualTo("admin");
            verify(memberMapper).updateById(member);
        }
    }

    @Nested
    @DisplayName("getMy / getJoined")
    class QueryLists {

        @Test
        @DisplayName("我创建的 → 返回列表")
        void shouldReturnMyKbs() {
            when(sharedKbMapper.selectList(any())).thenReturn(List.of(sample));

            var result = service.getMy(100L);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("我加入的 → 排除自己创建的")
        void shouldReturnJoinedKbs() {
            SharedKb others = new SharedKb();
            others.setId(2L);
            others.setOwnerId(200L); // 别人的
            when(sharedKbMapper.selectJoined(100L)).thenReturn(List.of(sample, others));

            var result = service.getJoined(100L);

            assertThat(result).hasSize(1); // 只返回别人的
            assertThat(result.get(0).getOwnerId()).isEqualTo(200L);
        }
    }
}
