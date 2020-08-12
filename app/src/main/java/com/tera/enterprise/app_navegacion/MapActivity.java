package com.tera.enterprise.app_navegacion;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;
import com.mapbox.android.core.location.LocationEngine;
import com.mapbox.android.core.location.LocationEngineCallback;
import com.mapbox.android.core.location.LocationEngineProvider;
import com.mapbox.android.core.location.LocationEngineRequest;
import com.mapbox.android.core.location.LocationEngineResult;
import com.mapbox.api.directions.v5.DirectionsCriteria;
import com.mapbox.api.directions.v5.models.DirectionsResponse;
import com.mapbox.api.directions.v5.models.DirectionsRoute;
import com.mapbox.core.constants.Constants;
import com.mapbox.geojson.LineString;
import com.mapbox.geojson.Point;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.exceptions.InvalidLatLngBoundsException;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.geometry.LatLngBounds;
import com.mapbox.mapboxsdk.location.LocationComponent;
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions;
import com.mapbox.mapboxsdk.location.modes.CameraMode;
import com.mapbox.mapboxsdk.location.modes.RenderMode;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.maps.Style;
import com.mapbox.services.android.navigation.ui.v5.NavigationLauncher;
import com.mapbox.services.android.navigation.ui.v5.NavigationLauncherOptions;
import com.mapbox.services.android.navigation.ui.v5.route.NavigationMapRoute;
import com.mapbox.services.android.navigation.ui.v5.route.OnRouteSelectionChangeListener;
import com.mapbox.services.android.navigation.v5.navigation.NavigationRoute;

import org.jetbrains.annotations.NotNull;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import timber.log.Timber;

public class MapActivity extends AppCompatActivity implements OnMapReadyCallback,
        MapboxMap.OnMapLongClickListener, OnRouteSelectionChangeListener {

    private static final int DURACION_ANIMACION_CAMARA = 1000;
    private static final int ZOOM_POR_DEFECTO_CAMARA = 16;
    private static final int ZOOM_INICIAL = 16;
    private static final long INTERVALO_ACTUALIZACION = 1000; // en milisegundos
    private static final long MINIMO_INTERVALO_DE_ACTUALIZACION = 500; // en milisegundos

    private static final int PETICION_PERMISO = 30001;

    private final NavigationLauncherLocationCallback callback = new NavigationLauncherLocationCallback(this);
    private LocationEngine motorLocalizacion;
    private NavigationMapRoute mapaRuta;
    private MapboxMap mapboxMap;
    private Point localizacionActual;
    private Point destino;
    private DirectionsRoute ruta;
    private boolean localizacionEncontrada;

    private MapView vistaMapa;
    private Button botonCargarRuta;
    private ProgressBar cargando;
    private FrameLayout marcoBotonCargar;
    private static TextView speedWidget;
    private boolean instructionListShown;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Mapbox.getInstance(this, getString(R.string.mapbox_access_token));
        setContentView(R.layout.activity_map);
        speedWidget = findViewById(R.id.speed_limit);


        vistaMapa = findViewById(R.id.vistaMapa);
        botonCargarRuta = findViewById(R.id.boton_cargar_navegacion);
        cargando = findViewById(R.id.cargando);
        marcoBotonCargar = findViewById(R.id.marco_boton_cargar);

        if (permisos()) {
            vistaMapa.getMapAsync(this);
        }
    }

    private boolean permisos() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            boolean locationPermissionGranted = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            if (!locationPermissionGranted) {
                ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, PETICION_PERMISO);
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PETICION_PERMISO) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Necesita proporcionar los permisos para que la aplicación funcione correctamente", Toast.LENGTH_LONG);
                permisos();
            } else {
                vistaMapa.getMapAsync(this);
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        vistaMapa.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        vistaMapa.onResume();
        if (motorLocalizacion != null) {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Primero habilite el ACCESO A LA UBICACIÓN en la configuración.", Toast.LENGTH_LONG).show();
                return;
            }
            motorLocalizacion.requestLocationUpdates(buildEngineRequest(), callback, null);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        vistaMapa.onPause();
        if (motorLocalizacion != null) {
            motorLocalizacion.removeLocationUpdates(callback);
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        vistaMapa.onLowMemory();
    }

    @Override
    protected void onStop() {
        super.onStop();
        vistaMapa.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        vistaMapa.onSaveInstanceState(outState);
    }

    @Override
    public void onMapReady(@NonNull MapboxMap mapboxMap) {
        this.mapboxMap = mapboxMap;
        mapboxMap.setStyle(Style.MAPBOX_STREETS, style -> {
            mapboxMap.addOnMapLongClickListener(this);
            iniciarMotorLocalizacion();
            iniciarComponenteLocalizacion(style);
            iniciarMapaRuta();
        });
    }

    @Override
    public boolean onMapLongClick(@NonNull LatLng point) {
        destino = Point.fromLngLat(point.getLongitude(), point.getLatitude());
        botonCargarRuta.setEnabled(false);
        cargando.setVisibility(View.VISIBLE);
        if (localizacionActual != null) {
            obtenerRuta();
        }
        return false;
    }

    @Override
    public void onNewPrimaryRouteSelected(DirectionsRoute directionsRoute) {
        ruta = directionsRoute;
    }

    void actualizarLocalizacion(Point currentLocation) {
        this.localizacionActual = currentLocation;
    }

    void localizacionEncontrada(Location location) {
        if (!localizacionEncontrada) {
            animarCamara(new LatLng(location.getLatitude(), location.getLongitude()));
            Snackbar.make(vistaMapa, "Primero habilite el ACCESO A LA UBICACIÓN en la configuración", Snackbar.LENGTH_LONG).show();
            localizacionEncontrada = true;
            hideLoading();
        }
    }

    private void iniciarMotorLocalizacion() {
        motorLocalizacion = LocationEngineProvider.getBestLocationEngine(getApplicationContext());
        LocationEngineRequest request = buildEngineRequest();
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Primero habilite el ACCESO A LA UBICACIÓN en la configuración.", Toast.LENGTH_LONG).show();
            return;
        }
        motorLocalizacion.requestLocationUpdates(request, callback, null);
        motorLocalizacion.getLastLocation(callback);
    }

    private void iniciarComponenteLocalizacion(Style style) {
        LocationComponent locationComponent = mapboxMap.getLocationComponent();
        locationComponent.activateLocationComponent(
                LocationComponentActivationOptions.builder(this, style)
                        .locationEngine(motorLocalizacion).build());
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Primero habilite el ACCESO A LA UBICACIÓN en la configuración.", Toast.LENGTH_LONG).show();
            return;
        }
        locationComponent.setLocationComponentEnabled(true);
        locationComponent.setRenderMode(RenderMode.NORMAL);
        locationComponent.setCameraMode(CameraMode.TRACKING_COMPASS);
    }

    private void iniciarMapaRuta() {
        mapaRuta = new NavigationMapRoute(vistaMapa, mapboxMap);
        mapaRuta.setOnRouteSelectionChangeListener(this);
    }

    private void obtenerRuta() {
        NavigationRoute.Builder builder = NavigationRoute.builder(this)
                .accessToken(getString(R.string.mapbox_access_token))
                .origin(localizacionActual)
                .destination(destino)
                .language(new Locale("es"))
                .voiceUnits(DirectionsCriteria.METRIC)
                .alternatives(true);
        builder.build()
                .getRoute(new Callback<DirectionsResponse>() {
                    @Override
                    public void onResponse(@NotNull Call<DirectionsResponse> call, @NotNull Response<DirectionsResponse> response) {
                        if (esRutaValida(response)) {
                            hideLoading();
                            ruta = response.body().routes().get(0);
                            if (ruta.distance() > 25d) {
                                botonCargarRuta.setEnabled(true);
                                mapaRuta.addRoutes(response.body().routes());
                                enfocarCamaraEnRuta();
                            } else {
                                Snackbar.make(vistaMapa, "Error, seleccione una ruta más larga", Snackbar.LENGTH_SHORT).show();
                            }
                        }
                    }

                    @Override
                    public void onFailure(Call<DirectionsResponse> call, Throwable t) {
                    }
                });
        cargando.setVisibility(View.VISIBLE);
    }

    private boolean esRutaValida(Response<DirectionsResponse> response) {
        return response.body() != null && !response.body().routes().isEmpty();
    }

    private void hideLoading() {
        if (cargando.getVisibility() == View.VISIBLE) {
            cargando.setVisibility(View.INVISIBLE);
        }
    }

    public void enfocarCamaraEnRuta() {
        if (ruta != null) {
            List<Point> routeCoords = LineString.fromPolyline(ruta.geometry(),
                    Constants.PRECISION_6).coordinates();
            List<LatLng> bboxPoints = new ArrayList<>();
            for (Point point : routeCoords) {
                bboxPoints.add(new LatLng(point.latitude(), point.longitude()));
            }
            if (bboxPoints.size() > 1) {
                try {
                    LatLngBounds bounds = new LatLngBounds.Builder().includes(bboxPoints).build();
                    // left, top, right, bottom
                    int topPadding = marcoBotonCargar.getHeight() * 2;
                    animarCajaCamara(bounds, DURACION_ANIMACION_CAMARA, new int[] {50, topPadding, 50, 100});
                } catch (InvalidLatLngBoundsException exception) {
                    Toast.makeText(this, "Error, no se encontró una ruta válida", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void animarCajaCamara(LatLngBounds bounds, int animationTime, int[] padding) {
        CameraPosition position = mapboxMap.getCameraForLatLngBounds(bounds, padding);
        mapboxMap.animateCamera(CameraUpdateFactory.newCameraPosition(position), animationTime);
    }

    private void animarCamara(LatLng point) {
        mapboxMap.animateCamera(CameraUpdateFactory.newLatLngZoom(point, ZOOM_POR_DEFECTO_CAMARA), DURACION_ANIMACION_CAMARA);
    }

    @NonNull
    private LocationEngineRequest buildEngineRequest() {
        return new LocationEngineRequest.Builder(INTERVALO_ACTUALIZACION)
                .setPriority(LocationEngineRequest.PRIORITY_HIGH_ACCURACY)
                .setFastestInterval(MINIMO_INTERVALO_DE_ACTUALIZACION)
                .build();
    }

    public void lanzarRutaClick(View view) {
        if (ruta == null) {
            Snackbar.make(vistaMapa, "Error: no hay ruta al destino seleccionado", Snackbar.LENGTH_SHORT).show();
            return;
        }



        NavigationLauncherOptions.Builder optionsBuilder = NavigationLauncherOptions.builder()
                .shouldSimulateRoute(true);
        CameraPosition initialPosition = new CameraPosition.Builder()
                .target(new LatLng(localizacionActual.latitude(), localizacionActual.longitude()))
                .zoom(ZOOM_INICIAL)
                .build();
        optionsBuilder.initialMapCameraPosition(initialPosition);
        optionsBuilder.directionsRoute(ruta);
        NavigationLauncher.startNavigation(this, optionsBuilder.build());
    }



    private static class NavigationLauncherLocationCallback implements LocationEngineCallback<LocationEngineResult> {

        private final WeakReference<MapActivity> activityWeakReference;
        private boolean instructionListShown = true;

        NavigationLauncherLocationCallback(MapActivity activity) {
            this.activityWeakReference = new WeakReference<>(activity);
        }

        @Override
        public void onSuccess(LocationEngineResult result) {
            MapActivity activity = activityWeakReference.get();
            if (activity != null) {
                Location location = result.getLastLocation();
                if (location == null) {
                    return;
                }
                activity.actualizarLocalizacion(
                        Point.fromLngLat(
                                location.getLongitude(),
                                location.getLatitude()));
                activity.localizacionEncontrada(location);
                setSpeed(location);
            }
        }

        @Override
        public void onFailure(@NonNull Exception exception) {
            Timber.e(exception);
        }
        private void setSpeed(Location location) {
            String string = String.format("%d\nKMH", (int) (location.getSpeed()*3600)/1000);
            int mphTextSize = 100;
            int speedTextSize = 100;

            SpannableString spannableString = new SpannableString(string);
            spannableString.setSpan(new AbsoluteSizeSpan(mphTextSize),
                    string.length() - 4, string.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);

            spannableString.setSpan(new AbsoluteSizeSpan(speedTextSize),
                    0, string.length() - 3, Spanned.SPAN_INCLUSIVE_INCLUSIVE);

            speedWidget.setText(spannableString);
            if (!instructionListShown) {
                speedWidget.setVisibility(View.VISIBLE);
            }
        }


    }
}
