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

package com.google.gerrit.server.change;

import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.update.RetryHelper;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

@Singleton
public class DeletePrivateByPost extends DeletePrivate implements UiAction<ChangeResource> {
  @Inject
  DeletePrivateByPost(
      Provider<ReviewDb> dbProvider,
      RetryHelper retryHelper,
      ChangeMessagesUtil cmUtil,
      PermissionBackend permissionBackend) {
    super(dbProvider, retryHelper, cmUtil, permissionBackend);
  }

  @Override
  public Description getDescription(ChangeResource rsrc) {
    return new UiAction.Description()
        .setLabel("Unmark private")
        .setTitle("Unmark change as private")
        .setVisible(rsrc.getChange().isPrivate() && canDeletePrivate(rsrc));
  }
}
