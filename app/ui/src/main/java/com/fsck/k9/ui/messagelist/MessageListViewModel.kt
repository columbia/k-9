package com.fsck.k9.ui.messagelist

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.fsck.k9.Account
import com.fsck.k9.mailstore.Folder
import com.fsck.k9.mailstore.FolderRepositoryManager

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.jetbrains.anko.coroutines.experimental.bg

class MessageListViewModel(private val folderRepositoryManager: FolderRepositoryManager) : ViewModel() {
    private val foldersLiveData = MutableLiveData<List<Folder>>()


    fun getFolders(account: Account): LiveData<List<Folder>> {
        if (foldersLiveData.value == null) {
            loadFolders(account)
        }

        return foldersLiveData
    }

    private fun loadFolders(account: Account) {
        GlobalScope.launch(Dispatchers.Main) {
            val folders = bg {
                val folderRepository = folderRepositoryManager.getFolderRepository(account)
                folderRepository.getDisplayFolders()
            }.await()

            foldersLiveData.value = folders
        }
    }
}
