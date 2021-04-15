package com.example.appwritedemoapplication.ui.Accounts

import android.app.Activity
import android.content.Context
import android.text.Editable
import androidx.activity.ComponentActivity
import androidx.lifecycle.*
import com.example.appwritedemoapplication.utils.Client.client
import com.example.appwritedemoapplication.utils.Event
import io.appwrite.exceptions.AppwriteException
import io.appwrite.services.AccountService
import kotlinx.coroutines.launch
import org.json.JSONObject


class AccountsViewModel : ViewModel() {

    private val _error = MutableLiveData<Event<Exception>>().apply {
        value = null
    }
    val error: LiveData<Event<Exception>> = _error

    private val _response = MutableLiveData<Event<String>>().apply {
        value = null
    }
    val response: LiveData<Event<String>> = _response

    private val accountService by lazy {
        AccountService(client)
    }

    fun onLogin(email: Editable , password : Editable) {
        viewModelScope.launch {
            try {
                var response = accountService.createSession(email.toString(), password.toString())
                var json = response.body?.string() ?: ""
                json = JSONObject(json).toString(8)
                _response.postValue(Event(json))
            } catch (e: AppwriteException) {
                _error.postValue(Event(e))
            }
        }

    }

    fun onSignup(email: Editable , password : Editable, name: Editable) {
        viewModelScope.launch {
            try {
                var response = accountService.create(email.toString(), password.toString(), name.toString())
                var json = response.body?.string() ?: ""
                json = JSONObject(json).toString(2)
                _response.postValue(Event(json))
            } catch (e: AppwriteException) {
                _error.postValue(Event(e))
            }
        }

    }

    fun oAuthLogin(activity: ComponentActivity) {
        viewModelScope.launch {
            try {
                accountService.createOAuth2Session(activity, "facebook", "appwrite-callback-6062f9c2c09ce://demo.appwrite.io/auth/oauth2/success", "appwrite-callback-6062f9c2c09ce://demo.appwrite.io/auth/oauth2/failure")
            } catch (e: Exception) {
                _error.postValue(Event(e))
            }
        }
    }

    fun getUser() {
        viewModelScope.launch {
            try {
                var response = accountService.get()
                var json = response.body?.string() ?: ""
                json = JSONObject(json).toString(2)
                _response.postValue(Event(json))
            } catch (e: AppwriteException) {
                _error.postValue(Event(e))
            }
        }
    }

}