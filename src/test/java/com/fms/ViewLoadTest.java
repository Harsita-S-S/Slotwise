package com.fms;

import com.fms.model.Role;
import com.fms.model.User;
import com.fms.service.SessionManager;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ViewLoadTest {

    @BeforeAll
    public static void initJFX() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException e) {
            // Already initialized
        }
        User mockAdmin = new User("admin", "hash", Role.Admin, "Admin User", "admin@fms.com");
        SessionManager.getInstance().setCurrentUser(mockAdmin);
    }

    @Test
    public void testLoadBookingsFxml() throws Exception {
        URL url = getClass().getResource("/com/fms/view/bookings.fxml");
        assertNotNull(url, "bookings.fxml not found");
        FXMLLoader loader = new FXMLLoader(url);
        Object root = loader.load();
        assertNotNull(root, "bookings root should not be null");
    }

    @Test
    public void testLoadDashboardFxml() throws Exception {
        URL url = getClass().getResource("/com/fms/view/dashboard.fxml");
        assertNotNull(url, "dashboard.fxml not found");
        FXMLLoader loader = new FXMLLoader(url);
        Object root = loader.load();
        assertNotNull(root, "dashboard root should not be null");
    }

    @Test
    public void testLoadFacilitiesFxml() throws Exception {
        URL url = getClass().getResource("/com/fms/view/facilities.fxml");
        assertNotNull(url, "facilities.fxml not found");
        FXMLLoader loader = new FXMLLoader(url);
        Object root = loader.load();
        assertNotNull(root, "facilities root should not be null");
    }

    @Test
    public void testLoadEquipmentFxml() throws Exception {
        URL url = getClass().getResource("/com/fms/view/equipment.fxml");
        assertNotNull(url, "equipment.fxml not found");
        FXMLLoader loader = new FXMLLoader(url);
        Object root = loader.load();
        assertNotNull(root, "equipment root should not be null");
    }

    @Test
    public void testLoadStaffFxml() throws Exception {
        URL url = getClass().getResource("/com/fms/view/staff.fxml");
        assertNotNull(url, "staff.fxml not found");
        FXMLLoader loader = new FXMLLoader(url);
        Object root = loader.load();
        assertNotNull(root, "staff root should not be null");
    }

    @Test
    public void testLoadBookingDialogFxml() throws Exception {
        URL url = getClass().getResource("/com/fms/view/booking_dialog.fxml");
        assertNotNull(url, "booking_dialog.fxml not found");
        FXMLLoader loader = new FXMLLoader(url);
        javafx.scene.Parent root = loader.load();
        assertNotNull(root, "booking dialog root should not be null");

        // Verify FXML namespace resolution (reliable way to access FXML nodes)
        assertNotNull(loader.getNamespace().get("dlgFacilityCombo"), "dlgFacilityCombo namespace lookup");
        assertNotNull(loader.getNamespace().get("rbSingleDay"), "rbSingleDay namespace lookup");
        assertNotNull(loader.getNamespace().get("rbMultiDay"), "rbMultiDay namespace lookup");
        assertNotNull(loader.getNamespace().get("dlgDatePicker"), "dlgDatePicker namespace lookup");
        assertNotNull(loader.getNamespace().get("dlgStartDatePicker"), "dlgStartDatePicker namespace lookup");
        assertNotNull(loader.getNamespace().get("dlgEndDatePicker"), "dlgEndDatePicker namespace lookup");
        assertNotNull(loader.getNamespace().get("chkFullDay"), "chkFullDay namespace lookup");
        assertNotNull(loader.getNamespace().get("comboStartTime"), "comboStartTime namespace lookup");
        assertNotNull(loader.getNamespace().get("comboEndTime"), "comboEndTime namespace lookup");
        assertNotNull(loader.getNamespace().get("dlgPurposeField"), "dlgPurposeField namespace lookup");
        assertNotNull(loader.getNamespace().get("dlgAttendeesField"), "dlgAttendeesField namespace lookup");
        assertNotNull(loader.getNamespace().get("btnConfirm"), "btnConfirm namespace lookup");
        assertNotNull(loader.getNamespace().get("btnCancel"), "btnCancel namespace lookup");
    }
}
