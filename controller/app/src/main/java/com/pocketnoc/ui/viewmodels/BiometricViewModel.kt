package com.pocketnoc.ui.viewmodels

import androidx.lifecycle.ViewModel
import com.pocketnoc.utils.BiometricAuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Expõe o BiometricAuthManager (@Singleton) para o BiometricGateScreen via Hilt,
 * sem a tela precisar conhecer a infra de DI.
 */
@HiltViewModel
class BiometricViewModel @Inject constructor(
    val biometricManager: BiometricAuthManager
) : ViewModel()
