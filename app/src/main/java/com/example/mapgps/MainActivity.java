package com.example.mapgps;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.gms.location.CurrentLocationRequest;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.gms.tasks.CancellationTokenSource;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Rumbo: una bitácora personal de lugares, coordenadas y recuerdos. */
public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {
    private static final String DATA_FILE = "rumbo_places.json";
    private final ExecutorService diskWorker = Executors.newSingleThreadExecutor();
    private final List<Place> places = new ArrayList<>();
    private GoogleMap map;
    private FusedLocationProviderClient locationClient;
    private LocationCallback locationCallback;
    private boolean locationUpdatesStarted;
    private TextView locationText, placesCount;
    private android.view.View topPanel, bottomPanel;
    private FloatingActionButton mapExpandButton;
    private boolean mapExpanded;
    private ImageView photoPreview;
    private android.view.View emptyPhotoBadge;
    private Uri pendingPhotoUri;
    private LatLng currentLocation;
    private LatLng selectedLocation;

    private final ActivityResultLauncher<String[]> locationPermission = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION))
                        || Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_COARSE_LOCATION))) locateUser();
                else locationText.setText("Ubicación desactivada · toca el mapa para guardar lugares");
            });
    private final ActivityResultLauncher<String> cameraPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) launchCamera();
                else Toast.makeText(this, "Necesitamos permiso de cámara para tomar la foto", Toast.LENGTH_SHORT).show();
            });
    private final ActivityResultLauncher<Uri> takePicture = registerForActivityResult(
            new ActivityResultContracts.TakePicture(), success -> {
                if (success && pendingPhotoUri != null) {
                    photoPreview.setImageURI(pendingPhotoUri);
                    photoPreview.setVisibility(ImageView.VISIBLE);
                    emptyPhotoBadge.setVisibility(android.view.View.GONE);
                    savePhotoReference(pendingPhotoUri.toString());
                    Toast.makeText(this, "Foto guardada en tu bitácora", Toast.LENGTH_SHORT).show();
                }
            });

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        locationClient = LocationServices.getFusedLocationProviderClient(this);
        locationCallback = new LocationCallback() {
            @Override public void onLocationResult(LocationResult result) {
                Location location = result.getLastLocation();
                if (location == null) return;
                currentLocation = new LatLng(location.getLatitude(), location.getLongitude());
                locationText.setText(String.format(java.util.Locale.getDefault(),
                        "EN VIVO · %.5f, %.5f · ±%.0f m", location.getLatitude(), location.getLongitude(), location.getAccuracy()));
            }
        };
        locationText = findViewById(R.id.locationText);
        placesCount = findViewById(R.id.placesCount);
        topPanel = findViewById(R.id.topPanel);
        bottomPanel = findViewById(R.id.bottomPanel);
        mapExpandButton = findViewById(R.id.mapExpandButton);
        photoPreview = findViewById(R.id.photoPreview);
        emptyPhotoBadge = findViewById(R.id.emptyPhotoBadge);
        ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map)).getMapAsync(this);
        ((FloatingActionButton) findViewById(R.id.myLocationButton)).setOnClickListener(v -> requestLocation());
        mapExpandButton.setOnClickListener(v -> toggleMapExpanded());
        findViewById(R.id.deletePlacesButton).setOnClickListener(v -> showClearPointsConfirmation());
        ((MaterialButton) findViewById(R.id.cameraButton)).setOnClickListener(v -> requestCamera());
        loadPlaces();
    }

    @Override public void onMapReady(GoogleMap googleMap) {
        map = googleMap;
        map.getUiSettings().setCompassEnabled(true);
        map.getUiSettings().setMapToolbarEnabled(false);
        map.getUiSettings().setZoomControlsEnabled(false);
        map.getUiSettings().setMyLocationButtonEnabled(false);
        map.setPadding(dp(12), dp(126), dp(72), dp(166));
        try { map.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.rumbo_map_style)); }
        catch (Exception ignored) { }
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(-35.6751, -71.5430), 4.8f));
        map.setOnMapClickListener(point -> addPlace(point, true));
        map.setOnMarkerClickListener(marker -> {
            selectedLocation = marker.getPosition();
            marker.showInfoWindow();
            return false;
        });
        renderPlaces();
        requestLocation();
    }

    private void requestLocation() {
        boolean fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!fine && !coarse) {
            locationPermission.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
            return;
        }
        locationText.setText("Obteniendo ubicación GPS…");
        locateUser();
    }

    private void locateUser() {
        if (map == null) return;
        boolean fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!fine && !coarse) return;
        try {
            map.setMyLocationEnabled(true);
            startLocationUpdates(fine);
            CurrentLocationRequest request = new CurrentLocationRequest.Builder()
                    .setPriority(fine ? Priority.PRIORITY_HIGH_ACCURACY : Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                    .setMaxUpdateAgeMillis(10_000).setDurationMillis(20_000).build();
            locationClient.getCurrentLocation(request, new CancellationTokenSource().getToken())
                    .addOnSuccessListener(this, location -> {
                        if (location == null) {
                            locationText.setText("Sin señal GPS · revisa que la ubicación esté activada");
                            return;
                        }
                        currentLocation = new LatLng(location.getLatitude(), location.getLongitude());
                        selectedLocation = currentLocation;
                        locationText.setText(String.format(java.util.Locale.getDefault(),
                                "EN VIVO · %.5f, %.5f · ±%.0f m", location.getLatitude(), location.getLongitude(), location.getAccuracy()));
                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 16f));
                    }).addOnFailureListener(this, error -> locationText.setText("No pudimos obtener la ubicación"));
        } catch (SecurityException ignored) { }
    }

    private void startLocationUpdates(boolean fineLocation) {
        if (locationUpdatesStarted || locationCallback == null) return;
        long intervalMs = 5_000L;
        LocationRequest request = new LocationRequest.Builder(
                fineLocation ? Priority.PRIORITY_HIGH_ACCURACY : Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                intervalMs)
                .setMinUpdateIntervalMillis(2_000L)
                .setMaxUpdateDelayMillis(10_000L)
                .build();
        locationUpdatesStarted = true;
        try {
            locationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
                    .addOnFailureListener(this, error -> locationUpdatesStarted = false);
        } catch (SecurityException ignored) {
            locationUpdatesStarted = false;
        }
    }

    private void stopLocationUpdates() {
        if (!locationUpdatesStarted || locationCallback == null) return;
        locationClient.removeLocationUpdates(locationCallback);
        locationUpdatesStarted = false;
    }

    private void addPlace(LatLng point, boolean persist) {
        selectedLocation = point;
        Place place = new Place(point.latitude, point.longitude);
        places.add(place);
        if (map != null) map.addMarker(new MarkerOptions().position(point).icon(BitmapDescriptorFactory.defaultMarker(148f)).title("Lugar " + places.size())
                .snippet(String.format(java.util.Locale.getDefault(), "%.5f, %.5f", point.latitude, point.longitude)));
        placesCount.setText(places.size() + (places.size() == 1 ? " punto" : " puntos"));
        if (persist) persistPlaces();
        Toast.makeText(this, "Lugar guardado · " + String.format(java.util.Locale.getDefault(), "%.5f, %.5f", point.latitude, point.longitude), Toast.LENGTH_SHORT).show();
    }

    private void requestCamera() {
        if (selectedLocation == null) selectedLocation = currentLocation;
        if (selectedLocation == null) {
            Toast.makeText(this, "Primero obtén tu ubicación o toca un lugar del mapa", Toast.LENGTH_LONG).show();
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchCamera();
        else cameraPermission.launch(Manifest.permission.CAMERA);
    }

    private void launchCamera() {
        try {
            File folder = new File(getFilesDir(), "photos");
            if (!folder.exists() && !folder.mkdirs()) throw new IllegalStateException("No se pudo crear la carpeta de fotos");
            File photo = new File(folder, "rumbo_" + System.currentTimeMillis() + ".jpg");
            pendingPhotoUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photo);
            takePicture.launch(pendingPhotoUri);
        } catch (Exception error) { Toast.makeText(this, "No se pudo abrir la cámara", Toast.LENGTH_SHORT).show(); }
    }

    private void savePhotoReference(String uri) {
        LatLng target = selectedLocation;
        if (target == null) return;
        diskWorker.execute(() -> {
            try {
                JSONObject data = readData();
                JSONArray photos = data.optJSONArray("photos");
                if (photos == null) photos = new JSONArray();
                JSONObject entry = new JSONObject();
                entry.put("lat", target.latitude); entry.put("lng", target.longitude); entry.put("uri", uri);
                photos.put(entry); data.put("photos", photos); writeData(data);
            } catch (Exception ignored) { }
        });
    }

    private void loadPlaces() {
        diskWorker.execute(() -> {
            JSONObject data = readData();
            JSONArray saved = data.optJSONArray("places");
            JSONArray savedPhotos = data.optJSONArray("photos");
            List<Place> loaded = new ArrayList<>();
            if (saved != null) for (int i = 0; i < saved.length(); i++) {
                JSONObject item = saved.optJSONObject(i);
                if (item != null) loaded.add(new Place(item.optDouble("lat"), item.optDouble("lng")));
            }
            String latestPhoto = null;
            if (savedPhotos != null && savedPhotos.length() > 0) {
                JSONObject photo = savedPhotos.optJSONObject(savedPhotos.length() - 1);
                if (photo != null) latestPhoto = photo.optString("uri", null);
            }
            final String photoUri = latestPhoto;
            runOnUiThread(() -> {
                places.addAll(loaded); renderPlaces();
                if (photoUri != null) {
                    photoPreview.setImageURI(Uri.parse(photoUri));
                    if (photoPreview.getDrawable() != null) {
                        photoPreview.setVisibility(ImageView.VISIBLE);
                        emptyPhotoBadge.setVisibility(android.view.View.GONE);
                    }
                }
            });
        });
    }

    private void renderPlaces() {
        if (map == null) return;
        for (int i = 0; i < places.size(); i++) {
            Place p = places.get(i);
            map.addMarker(new MarkerOptions().position(new LatLng(p.lat, p.lng)).icon(BitmapDescriptorFactory.defaultMarker(148f)).title("Lugar " + (i + 1))
                    .snippet(String.format(java.util.Locale.getDefault(), "%.5f, %.5f", p.lat, p.lng)));
        }
        placesCount.setText(places.size() + (places.size() == 1 ? " punto" : " puntos"));
    }

    private void toggleMapExpanded() {
        mapExpanded = !mapExpanded;
        topPanel.setVisibility(mapExpanded ? android.view.View.GONE : android.view.View.VISIBLE);
        bottomPanel.setVisibility(mapExpanded ? android.view.View.GONE : android.view.View.VISIBLE);
        mapExpandButton.setImageResource(mapExpanded ? R.drawable.ic_collapse_map : R.drawable.ic_expand_map);
        mapExpandButton.setContentDescription(mapExpanded ? "Reducir mapa" : "Expandir mapa");
        if (map != null) {
            int horizontal = dp(mapExpanded ? 8 : 12);
            map.setPadding(horizontal, dp(mapExpanded ? 8 : 126), dp(mapExpanded ? 8 : 72), dp(mapExpanded ? 8 : 166));
        }
    }

    private void showClearPointsConfirmation() {
        if (places.isEmpty()) {
            Toast.makeText(this, "No hay puntos guardados para borrar", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("¿Borrar todos los puntos?")
                .setMessage("Se quitarán de la bitácora todos los marcadores guardados. Esta acción no se puede deshacer.")
                .setNegativeButton("Cancelar", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("Borrar todo", (dialog, which) -> clearAllPoints())
                .show();
    }

    private void clearAllPoints() {
        places.clear();
        selectedLocation = currentLocation;
        if (map != null) map.clear();
        placesCount.setText("0 puntos");
        persistPlaces();
        if (map != null) {
            try {
                boolean fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
                boolean coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
                if (fine || coarse) map.setMyLocationEnabled(true);
            } catch (SecurityException ignored) { }
        }
        Toast.makeText(this, "Se borraron todos los puntos", Toast.LENGTH_SHORT).show();
    }

    private void persistPlaces() {
        JSONArray items = new JSONArray();
        for (Place p : places) {
            JSONObject item = new JSONObject();
            try { item.put("lat", p.lat); item.put("lng", p.lng); items.put(item); }
            catch (Exception ignored) { }
        }
        diskWorker.execute(() -> {
            JSONObject data = readData();
            try { data.put("places", items); writeData(data); } catch (Exception ignored) { }
        });
    }

    private JSONObject readData() {
        File file = new File(getFilesDir(), DATA_FILE);
        if (!file.exists()) return new JSONObject();
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] bytes = new byte[(int) file.length()]; int count = in.read(bytes);
            return new JSONObject(new String(bytes, 0, Math.max(0, count), StandardCharsets.UTF_8));
        } catch (Exception ignored) { return new JSONObject(); }
    }

    private void writeData(JSONObject data) {
        File file = new File(getFilesDir(), DATA_FILE);
        try (FileOutputStream out = new FileOutputStream(file)) { out.write(data.toString().getBytes(StandardCharsets.UTF_8)); }
        catch (Exception ignored) { }
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override protected void onResume() {
        super.onResume();
        boolean fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if ((fine || coarse) && map != null) startLocationUpdates(fine);
    }

    @Override protected void onPause() {
        stopLocationUpdates();
        super.onPause();
    }

    @Override protected void onDestroy() { super.onDestroy(); diskWorker.shutdown(); }
    private static class Place {
        final double lat, lng;
        Place(double lat, double lng) { this.lat = lat; this.lng = lng; }
    }
}
