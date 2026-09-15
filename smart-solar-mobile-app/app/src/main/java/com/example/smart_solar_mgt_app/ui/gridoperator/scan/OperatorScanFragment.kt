package com.example.smart_solar_mgt_app.ui.gridoperator.scan

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.smart_solar_mgt_app.R
import com.example.smart_solar_mgt_app.core.common.AppError
import com.example.smart_solar_mgt_app.core.common.AppResult
import com.example.smart_solar_mgt_app.di.ServiceLocator
import com.example.smart_solar_mgt_app.domain.model.EnergyTransferVerification
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Live camera QR scanning (CameraX + ML Kit Barcode Scanning) - a different library from the
 * ZXing bitmap *generation* used for the Prosumer's Energy Transfer Pass (Phase 12), deliberately.
 *
 * Two-step flow: a decoded token is only verified (TransactionRepository.verifyToken - read-only,
 * no writes) and shown in a bottom sheet for the operator to review; completing the transfer
 * (TransactionRepository.completeTransfer) requires an explicit "Complete Transfer" tap. This
 * replaces Phase 13's single-step onFrameDecoded -> completeTransactionByToken design.
 */
class OperatorScanFragment : Fragment(R.layout.fragment_operator_scan) {

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
    )
    private var cameraExecutor: ExecutorService? = null

    @Volatile
    private var isProcessing = false

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else showPermissionUi(true)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<MaterialButton>(R.id.btnGrantPermission).setOnClickListener {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        if (hasCameraPermission()) {
            showPermissionUi(false)
            startCamera()
        } else {
            showPermissionUi(true)
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onResume() {
        super.onResume()
        isProcessing = false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor?.shutdown()
        cameraExecutor = null
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun showPermissionUi(showRequest: Boolean) {
        view?.findViewById<View>(R.id.groupNoPermission)?.isVisible = showRequest
        view?.findViewById<View>(R.id.previewView)?.isVisible = !showRequest
        view?.findViewById<View>(R.id.tvScanHint)?.isVisible = !showRequest
    }

    private fun startCamera() {
        showPermissionUi(false)
        val previewView = view?.findViewById<PreviewView>(R.id.previewView) ?: return
        val executor = Executors.newSingleThreadExecutor().also { cameraExecutor = it }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().apply {
                surfaceProvider = previewView.surfaceProvider
            }

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .apply { setAnalyzer(executor) { proxy -> analyzeFrame(proxy) } }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(viewLifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (e: IllegalStateException) {
                // Fragment view already torn down before the future completed - nothing to bind to.
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun analyzeFrame(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null || isProcessing) {
            imageProxy.close()
            return
        }
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val rawValue = barcodes.firstOrNull { it.rawValue != null }?.rawValue
                if (rawValue != null && !isProcessing) {
                    isProcessing = true
                    onTokenScanned(rawValue)
                }
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    private fun onTokenScanned(rawToken: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { ServiceLocator.transactionRepository.verifyToken(rawToken) }
            if (!isAdded) return@launch
            when (result) {
                is AppResult.Success -> showVerificationSheet(result.data)
                is AppResult.Failure -> showResultDialog("Scan Failed", failureMessage(result.error))
            }
        }
    }

    private fun showVerificationSheet(verification: EnergyTransferVerification) {
        val dialog = BottomSheetDialog(requireContext())
        val sheetView = layoutInflater.inflate(R.layout.bottomsheet_transfer_verification, null)
        sheetView.findViewById<TextView>(R.id.tvVerifyStation).text = verification.stationName
        sheetView.findViewById<TextView>(R.id.tvVerifyNic).text = "NIC: ${verification.prosumerNic}"
        sheetView.findViewById<TextView>(R.id.tvVerifyDateTime).text = "${verification.bookingDate} at ${verification.bookingTime}"
        sheetView.findViewById<TextView>(R.id.tvVerifyEnergy).text = "${verification.energyAmount} kWh"

        val btnComplete = sheetView.findViewById<MaterialButton>(R.id.btnCompleteTransfer)
        val btnCancel = sheetView.findViewById<MaterialButton>(R.id.btnCancelVerification)

        btnCancel.setOnClickListener {
            dialog.dismiss()
            isProcessing = false
        }
        btnComplete.setOnClickListener {
            btnComplete.isEnabled = false
            btnCancel.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                val completeResult = withContext(Dispatchers.IO) {
                    ServiceLocator.transactionRepository.completeTransfer(verification.transactionId)
                }
                dialog.dismiss()
                if (isAdded) {
                    when (completeResult) {
                        is AppResult.Success -> showResultDialog(
                            "Transfer Complete",
                            "Transaction ${completeResult.data.transactionId} marked COMPLETED."
                        )
                        is AppResult.Failure -> showResultDialog("Transfer Failed", failureMessage(completeResult.error))
                    }
                }
            }
        }

        // Not cancelable - the operator must explicitly choose Complete Transfer or Cancel so
        // isProcessing always gets reset by one of those handlers, never left stuck by an
        // outside-tap or back-press dismiss.
        dialog.setCancelable(false)
        dialog.setContentView(sheetView)
        dialog.show()
    }

    private fun showResultDialog(title: String, message: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK") { _, _ -> isProcessing = false }
            .setCancelable(false)
            .show()
    }

    private fun failureMessage(error: AppError): String = when (error) {
        AppError.NotFound -> "This transaction or booking no longer exists."
        AppError.InvalidStatusTransition -> "This pass has already been used or the booking is no longer approved."
        AppError.Unauthorized -> "You are not authorized to complete this transfer."
        is AppError.Unknown -> error.message
        else -> "Could not complete this transfer. Please try again."
    }
}
