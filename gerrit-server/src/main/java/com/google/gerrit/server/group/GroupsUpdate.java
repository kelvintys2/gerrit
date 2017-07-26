// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.server.group;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.audit.AuditService;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class GroupsUpdate {
  public interface Factory {
    GroupsUpdate create(@Nullable IdentifiedUser currentUser);
  }

  private final Groups groups;
  private final AuditService auditService;
  private final AccountCache accountCache;

  @Nullable private IdentifiedUser currentUser;

  @Inject
  GroupsUpdate(
      Groups groups,
      AuditService auditService,
      AccountCache accountCache,
      @Assisted @Nullable IdentifiedUser currentUser) {
    this.groups = groups;
    this.auditService = auditService;
    this.accountCache = accountCache;
    this.currentUser = currentUser;
  }

  /**
   * Uses the identity of the specified user to mark database modifications executed by this {@code
   * GroupsUpdate}. For NoteDb, this identity is used as author and committer for all related
   * commits.
   *
   * <p><strong>Note</strong>: Please use this method with care and rather consider to use the
   * correct annotation on the provider of this class instead.
   *
   * @param currentUser the user to which modifications should be attributed, or {@code null} if the
   *     Gerrit server identity should be used
   */
  public void setCurrentUser(@Nullable IdentifiedUser currentUser) {
    this.currentUser = currentUser;
  }

  public void addGroupMember(ReviewDb db, AccountGroup.NameKey groupName, Account.Id accountId)
      throws OrmException, IOException {
    Optional<AccountGroup> foundGroup = groups.get(db, groupName);
    if (!foundGroup.isPresent()) {
      // TODO(aliceks): Throw an exception?
      return;
    }

    AccountGroup group = foundGroup.get();
    addGroupMembers(db, group, ImmutableSet.of(accountId));
  }

  public void addGroupMember(ReviewDb db, AccountGroup.UUID groupUuid, Account.Id accountId)
      throws OrmException, IOException {
    Optional<AccountGroup> foundGroup = groups.get(db, groupUuid);
    if (!foundGroup.isPresent()) {
      // TODO(aliceks): Throw an exception?
      return;
    }

    AccountGroup group = foundGroup.get();
    addGroupMembers(db, group, ImmutableSet.of(accountId));
  }

  public void addGroupMember(ReviewDb db, AccountGroup.Id groupId, Account.Id accountId)
      throws OrmException, IOException {
    addGroupMembers(db, groupId, ImmutableSet.of(accountId));
  }

  public void addGroupMembers(ReviewDb db, AccountGroup.Id groupId, Set<Account.Id> accountIds)
      throws OrmException, IOException {
    Optional<AccountGroup> foundGroup = groups.get(db, groupId);
    if (!foundGroup.isPresent()) {
      // TODO(aliceks): Throw an exception?
      return;
    }

    AccountGroup group = foundGroup.get();
    addGroupMembers(db, group, accountIds);
  }

  private void addGroupMembers(ReviewDb db, AccountGroup group, Set<Account.Id> accountIds)
      throws OrmException, IOException {
    AccountGroup.Id groupId = group.getId();
    Set<AccountGroupMember> newMembers = new HashSet<>();
    for (Account.Id accountId : accountIds) {
      boolean isMember = groups.isMember(db, group, accountId);
      if (!isMember) {
        AccountGroupMember.Key key = new AccountGroupMember.Key(accountId, groupId);
        newMembers.add(new AccountGroupMember(key));
      }
    }

    if (newMembers.isEmpty()) {
      return;
    }

    if (currentUser != null) {
      auditService.dispatchAddAccountsToGroup(currentUser.getAccountId(), newMembers);
    }
    db.accountGroupMembers().insert(newMembers);
    for (AccountGroupMember newMember : newMembers) {
      accountCache.evict(newMember.getAccountId());
    }
  }

  public void removeGroupMembers(
      ReviewDb db, AccountGroup.UUID groupUuid, Set<Account.Id> accountIds)
      throws OrmException, IOException {
    Optional<AccountGroup> foundGroup = groups.get(db, groupUuid);
    if (!foundGroup.isPresent()) {
      // TODO(aliceks): Throw an exception?
      return;
    }

    AccountGroup group = foundGroup.get();
    AccountGroup.Id groupId = group.getId();
    Set<AccountGroupMember> membersToRemove = new HashSet<>();
    for (Account.Id accountId : accountIds) {
      boolean isMember = groups.isMember(db, group, accountId);
      if (isMember) {
        AccountGroupMember.Key key = new AccountGroupMember.Key(accountId, groupId);
        membersToRemove.add(new AccountGroupMember(key));
      }
    }

    if (membersToRemove.isEmpty()) {
      return;
    }

    if (currentUser != null) {
      auditService.dispatchDeleteAccountsFromGroup(currentUser.getAccountId(), membersToRemove);
    }
    db.accountGroupMembers().delete(membersToRemove);
    for (AccountGroupMember member : membersToRemove) {
      accountCache.evict(member.getAccountId());
    }
  }
}
