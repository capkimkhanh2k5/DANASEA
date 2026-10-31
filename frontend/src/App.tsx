import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { CustomerLayout } from "./layouts/CustomerLayout";
import { VendorLayout } from "./layouts/VendorLayout";
import { AdminLayout } from "./layouts/AdminLayout";
import { Home } from "./pages/customer/Home";
import { Explore } from "./pages/customer/Explore";
import { Auth } from "./pages/customer/Auth";
import { ForgotPassword } from "./pages/customer/Auth/ForgotPassword";
import { Search } from "./pages/customer/Search";
import { ExperienceDetail } from "./pages/customer/ExperienceDetail";
import { Cart } from "./pages/customer/Cart/Cart";
import { Checkout } from "./pages/customer/Checkout/Checkout";
import { PaymentResult } from "./pages/customer/Checkout/PaymentResult";
import { Profile } from "./pages/customer/Profile/Profile";
import { Orders } from "./pages/customer/Profile/Orders";
import { ChangePassword } from "./pages/customer/Profile/ChangePassword";
import { Wishlist } from "./pages/customer/Profile/Wishlist";

// Vendor Phase 4
import { Dashboard as VendorDashboard } from "./pages/vendor/Dashboard/Dashboard";
import { Services } from "./pages/vendor/Services/Services";
import { ServiceForm } from "./pages/vendor/Services/ServiceForm";
import { Schedule } from "./pages/vendor/Schedule/Schedule";
import { VendorOrders } from "./pages/vendor/Orders/VendorOrders";
import { Promotions } from "./pages/vendor/Promotions/Promotions";
import { VendorReviews } from "./pages/vendor/Reviews/VendorReviews";
import { Revenue } from "./pages/vendor/Revenue/Revenue";
import { Payouts } from "./pages/vendor/Revenue/Payouts";
import { Disputes as VendorDisputes } from "./pages/vendor/Disputes/Disputes";
import { DisputeDetail as VendorDisputeDetail } from "./pages/vendor/Disputes/DisputeDetail";
import { Profile as VendorProfile } from "./pages/vendor/Profile/Profile";

// Admin Phase 5
import { AdminDashboard } from "./pages/admin/Dashboard/AdminDashboard";
import { Users } from "./pages/admin/Users/Users";
import { VendorApproval } from "./pages/admin/VendorApproval/VendorApproval";
import { Categories } from "./pages/admin/Categories/Categories";
import { ServiceApproval } from "./pages/admin/ServiceApproval/ServiceApproval";
import { AdminOrders } from "./pages/admin/Orders/AdminOrders";
import { AdminPromotions } from "./pages/admin/Promotions/AdminPromotions";
import { AdminPayouts } from "./pages/admin/Payouts/AdminPayouts";
import { Disputes } from "./pages/admin/Disputes/Disputes";
import { Settings } from "./pages/admin/Settings/Settings";
import { Logs } from "./pages/admin/Logs/Logs";
import { DevRoleSwitcher } from "./components/common/DevRoleSwitcher";
import { PageLoader } from "./components/common/PageLoader";
import { ScrollToTop } from "./components/common/ScrollToTop";
import { ToastContainer } from "./components/common/ToastContainer";

function App() {
  return (
    <BrowserRouter>
      <ScrollToTop />
      <PageLoader />
      <ToastContainer />
      <DevRoleSwitcher />
      <Routes>
        {/* KHÁCH HÀNG */}
        <Route path="/" element={<CustomerLayout />}>
          <Route index element={<Home />} />
          <Route path="auth" element={<Auth />} />
          <Route path="auth/forgot-password" element={<ForgotPassword />} />
          <Route path="explore" element={<Explore />} />
          <Route path="search" element={<Search />} />
          <Route path="tour/:id" element={<ExperienceDetail />} />
          <Route path="experience/:id" element={<ExperienceDetail />} />
          
          {/* Phase 3 */}
          <Route path="cart" element={<Cart />} />
          <Route path="checkout" element={<Checkout />} />
          <Route path="checkout/result" element={<PaymentResult />} />
          <Route path="payment/vnpay-return" element={<PaymentResult />} />
          <Route path="profile" element={<Profile />} />
          <Route path="profile/orders" element={<Orders />} />
          <Route path="profile/ai-assistant" element={<Navigate to="/profile" replace />} />
          <Route path="profile/change-password" element={<ChangePassword />} />
          <Route path="profile/wishlist" element={<Wishlist />} />
          <Route path="inbox" element={<Navigate to="/profile" replace />} />
        </Route>

        {/* NHÀ CUNG CẤP */}
        <Route path="/vendor" element={<VendorLayout />}>
          <Route index element={<VendorDashboard />} />
          <Route path="profile" element={<VendorProfile />} />
          <Route path="services" element={<Services />} />
          <Route path="services/new" element={<ServiceForm />} />
          <Route path="services/:serviceId/edit" element={<ServiceForm />} />
          <Route path="services/:serviceId" element={<ServiceForm />} />
          <Route path="schedule" element={<Schedule />} />
          <Route path="orders" element={<VendorOrders />} />
          <Route path="promotions" element={<Promotions />} />
          <Route path="reviews" element={<VendorReviews />} />
          <Route path="revenue" element={<Revenue />} />
          <Route path="payouts" element={<Payouts />} />
          <Route path="disputes" element={<VendorDisputes />} />
          <Route path="disputes/:id" element={<VendorDisputeDetail />} />
        </Route>

        {/* QUẢN TRỊ VIÊN */}
        <Route path="/admin" element={<AdminLayout />}>
          <Route index element={<AdminDashboard />} />
          <Route path="users" element={<Users />} />
          <Route path="vendor-approval" element={<VendorApproval />} />
          <Route path="categories" element={<Categories />} />
          <Route path="service-approval" element={<ServiceApproval />} />
          <Route path="orders" element={<AdminOrders />} />
          <Route path="promotions" element={<AdminPromotions />} />
          <Route path="payouts" element={<AdminPayouts />} />
          <Route path="disputes" element={<Disputes />} />
          <Route path="settings" element={<Settings />} />
          <Route path="logs" element={<Logs />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}

export default App;
