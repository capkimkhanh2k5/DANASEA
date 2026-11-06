import { lazy, Suspense } from "react";
import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { CustomerLayout } from "./layouts/CustomerLayout";
import { Home } from "./pages/customer/Home";
import { DevRoleSwitcher } from "./components/common/DevRoleSwitcher";
import { ScrollToTop } from "./components/common/ScrollToTop";
import { ToastContainer } from "./components/common/ToastContainer";

// Lazy-loaded layouts & routes to keep the initial Homepage bundle small and fast
const VendorLayout = lazy(() => import("./layouts/VendorLayout").then((m) => ({ default: m.VendorLayout })));
const AdminLayout = lazy(() => import("./layouts/AdminLayout").then((m) => ({ default: m.AdminLayout })));

const Explore = lazy(() => import("./pages/customer/Explore").then((m) => ({ default: m.Explore })));
const Auth = lazy(() => import("./pages/customer/Auth").then((m) => ({ default: m.Auth })));
const ForgotPassword = lazy(() => import("./pages/customer/Auth/ForgotPassword").then((m) => ({ default: m.ForgotPassword })));
const Search = lazy(() => import("./pages/customer/Search").then((m) => ({ default: m.Search })));
const ExperienceDetail = lazy(() => import("./pages/customer/ExperienceDetail").then((m) => ({ default: m.ExperienceDetail })));
const Cart = lazy(() => import("./pages/customer/Cart/Cart").then((m) => ({ default: m.Cart })));
const Checkout = lazy(() => import("./pages/customer/Checkout/Checkout").then((m) => ({ default: m.Checkout })));
const PaymentResult = lazy(() => import("./pages/customer/Checkout/PaymentResult").then((m) => ({ default: m.PaymentResult })));
const Profile = lazy(() => import("./pages/customer/Profile/Profile").then((m) => ({ default: m.Profile })));
const Orders = lazy(() => import("./pages/customer/Profile/Orders").then((m) => ({ default: m.Orders })));
const ChangePassword = lazy(() => import("./pages/customer/Profile/ChangePassword").then((m) => ({ default: m.ChangePassword })));
const Wishlist = lazy(() => import("./pages/customer/Profile/Wishlist").then((m) => ({ default: m.Wishlist })));

// Vendor Phase 4
const VendorDashboard = lazy(() => import("./pages/vendor/Dashboard/Dashboard").then((m) => ({ default: m.Dashboard })));
const Services = lazy(() => import("./pages/vendor/Services/Services").then((m) => ({ default: m.Services })));
const ServiceForm = lazy(() => import("./pages/vendor/Services/ServiceForm").then((m) => ({ default: m.ServiceForm })));
const Schedule = lazy(() => import("./pages/vendor/Schedule/Schedule").then((m) => ({ default: m.Schedule })));
const VendorOrders = lazy(() => import("./pages/vendor/Orders/VendorOrders").then((m) => ({ default: m.VendorOrders })));
const Promotions = lazy(() => import("./pages/vendor/Promotions/Promotions").then((m) => ({ default: m.Promotions })));
const VendorReviews = lazy(() => import("./pages/vendor/Reviews/VendorReviews").then((m) => ({ default: m.VendorReviews })));
const Revenue = lazy(() => import("./pages/vendor/Revenue/Revenue").then((m) => ({ default: m.Revenue })));
const Payouts = lazy(() => import("./pages/vendor/Revenue/Payouts").then((m) => ({ default: m.Payouts })));
const VendorDisputes = lazy(() => import("./pages/vendor/Disputes/Disputes").then((m) => ({ default: m.Disputes })));
const VendorDisputeDetail = lazy(() => import("./pages/vendor/Disputes/DisputeDetail").then((m) => ({ default: m.DisputeDetail })));
const VendorProfile = lazy(() => import("./pages/vendor/Profile/Profile").then((m) => ({ default: m.Profile })));

// Admin Phase 5
const AdminDashboard = lazy(() => import("./pages/admin/Dashboard/AdminDashboard").then((m) => ({ default: m.AdminDashboard })));
const Users = lazy(() => import("./pages/admin/Users/Users").then((m) => ({ default: m.Users })));
const VendorApproval = lazy(() => import("./pages/admin/VendorApproval/VendorApproval").then((m) => ({ default: m.VendorApproval })));
const Categories = lazy(() => import("./pages/admin/Categories/Categories").then((m) => ({ default: m.Categories })));
const ServiceApproval = lazy(() => import("./pages/admin/ServiceApproval/ServiceApproval").then((m) => ({ default: m.ServiceApproval })));
const AdminOrders = lazy(() => import("./pages/admin/Orders/AdminOrders").then((m) => ({ default: m.AdminOrders })));
const AdminPromotions = lazy(() => import("./pages/admin/Promotions/AdminPromotions").then((m) => ({ default: m.AdminPromotions })));
const AdminPayouts = lazy(() => import("./pages/admin/Payouts/AdminPayouts").then((m) => ({ default: m.AdminPayouts })));
const Disputes = lazy(() => import("./pages/admin/Disputes/Disputes").then((m) => ({ default: m.Disputes })));
const Settings = lazy(() => import("./pages/admin/Settings/Settings").then((m) => ({ default: m.Settings })));
const Logs = lazy(() => import("./pages/admin/Logs/Logs").then((m) => ({ default: m.Logs })));

function App() {
  return (
    <BrowserRouter>
      <ScrollToTop />
      <ToastContainer />
      <DevRoleSwitcher />
      <Suspense fallback={<div className="min-h-screen bg-slate-50" />}>
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
          <Route path="payment/success" element={<PaymentResult />} />
          <Route path="payment/cancel" element={<PaymentResult />} />
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
      </Suspense>
    </BrowserRouter>
  );
}

export default App;
