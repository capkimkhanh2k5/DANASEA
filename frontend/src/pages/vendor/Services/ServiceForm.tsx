import { useState, useEffect, useCallback, type ChangeEvent } from 'react';
import { createPortal } from 'react-dom';
import { useNavigate, useParams } from 'react-router-dom';
import { vendorService } from '../../../services/vendorService';
import { categoryService } from '../../../services/categoryService';
import type {
  VendorServiceDto,
  CreateServiceRequest,
  UpdateServiceRequest,
  VendorServiceOptionDto,
  ServiceImageDto,
  SafetyDocumentDto,
  OptionType,
  PricingUnit,
} from '../../../types/vendorService';
import type { CategoryDto } from '../../../types/category';
import { toast } from '../../../store/useToastStore';
import { extractErrorMessage } from '../../../utils/errorHandler';

type FormTab = 'CONTENT_SAFETY' | 'IMAGES' | 'OPTIONS' | 'SAFETY_DOCS';

export function ServiceForm() {
  const { serviceId } = useParams<{ serviceId?: string }>();
  return <ServiceFormPage key={serviceId || 'new'} serviceId={serviceId} />;
}

function ServiceFormPage({ serviceId }: { serviceId: string | undefined }) {
  const navigate = useNavigate();
  const isEditMode = Boolean(serviceId);

  const [activeTab, setActiveTab] = useState<FormTab>('CONTENT_SAFETY');
  const [isLoading, setIsLoading] = useState<boolean>(Boolean(serviceId));
  const [isSaving, setIsSaving] = useState<boolean>(false);
  

  // Categories
  const [categories, setCategories] = useState<CategoryDto[]>([]);

  // Service State
  const [currentServiceId, setCurrentServiceId] = useState<string | null>(serviceId || null);
  const [serviceStatus, setServiceStatus] = useState<string>('DRAFT');
  const [rejectionReason, setRejectionReason] = useState<string | null>(null);

  // Tab 1: Content & Safety Form Fields
  const [nameVi, setNameVi] = useState('');
  const [nameEn, setNameEn] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [descriptionVi, setDescriptionVi] = useState('');
  const [descriptionEn, setDescriptionEn] = useState('');
  const [price, setPrice] = useState<number>(300000);
  const [durationMinutes, setDurationMinutes] = useState<number>(90);
  const [capacityPerSlot, setCapacityPerSlot] = useState<number>(10);
  const [locationName, setLocationName] = useState('Bãi tắm Sao Biển, Hoàng Sa, Sơn Trà');
  const [address, setAddress] = useState('Bãi tắm Sao Biển, Sơn Trà, Đà Nẵng');
  const [latitude, setLatitude] = useState<number | null>(16.0712);
  const [longitude, setLongitude] = useState<number | null>(108.2435);
  const [waiverRequired, setWaiverRequired] = useState<boolean>(true);
  const [waiverContent, setWaiverContent] = useState<string>(
    'Du khách cam kết đủ sức khỏe tham gia hoạt động biển, tuân thủ mặc áo phao cứu sinh trong suốt hành trình và chấp hành hiệu lệnh của huấn luyện viên/cứu hộ.'
  );
  const [waiverContentEn, setWaiverContentEn] = useState<string>('');
  const [weatherSensitive, setWeatherSensitive] = useState<boolean>(true);
  const [minWindKmh, setMinWindKmh] = useState<number | null>(10);
  const [maxWaveM, setMaxWaveM] = useState<number | null>(1.2);

  // Tab 2: Images
  const [images, setImages] = useState<ServiceImageDto[]>([]);
  const [isUploadingImage, setIsUploadingImage] = useState<boolean>(false);

  // Tab 3: Options
  const [options, setOptions] = useState<VendorServiceOptionDto[]>([]);
  const [isAddOptionModalOpen, setIsAddOptionModalOpen] = useState<boolean>(false);
  const [optName, setOptName] = useState('');
  const [optType, setOptType] = useState<OptionType>('SHARED');
  const [optPricingUnit, setOptPricingUnit] = useState<PricingUnit>('PER_PERSON');
  const [optPrice, setOptPrice] = useState<number>(300000);
  const [optMaxPax, setOptMaxPax] = useState<number | null>(null);
  const [optBenefits, setOptBenefits] = useState('');

  // Tab 4: Safety Documents
  const [safetyDocs, setSafetyDocs] = useState<SafetyDocumentDto[]>([]);
  const [isUploadingDoc, setIsUploadingDoc] = useState<boolean>(false);

  // Preview Modal
  const [previewOpen, setPreviewOpen] = useState<boolean>(false);

  const showToast = (msg: string) => {
    toast.success(msg);
  };

  // Load initial data
  const loadServiceData = useCallback((id: string) => {
    return Promise.all([
      vendorService.getServiceDetail(id),
      vendorService.getOptions(id).catch(() => []),
    ]).then(([svc, opts]) => {
      setCurrentServiceId(svc.id);
      setServiceStatus(svc.status);
      setRejectionReason(svc.rejectionReason);
      setNameVi(svc.name || '');
      setNameEn(svc.nameEn || '');
      setCategoryId(svc.categoryId || '');
      setDescriptionVi(svc.description || '');
      setDescriptionEn(svc.descriptionEn || '');
      setPrice(svc.price || 0);
      setDurationMinutes(svc.durationMinutes || 60);
      setCapacityPerSlot(svc.capacityPerSlot || 10);
      setLocationName(svc.locationName || '');
      setAddress(svc.address || '');
      setLatitude(svc.latitude);
      setLongitude(svc.longitude);
      setWaiverRequired(Boolean(svc.waiverRequired));
      setWaiverContent(svc.waiverContent || '');
      setWaiverContentEn(svc.waiverContentEn || '');
      setWeatherSensitive(Boolean(svc.weatherSensitive));
      setMinWindKmh(svc.minWindKmh);
      setMaxWaveM(svc.maxWaveM);
      setImages(svc.images || []);
      setOptions(opts || []);
    }).catch((err: unknown) => {
      console.error('[ServiceForm] Lỗi nạp thông tin dịch vụ:', err);
      const msg = extractErrorMessage(err, 'Không thể tải thông tin chi tiết dịch vụ');
      toast.error(msg);
    }).finally(() => {
      setIsLoading(false);
    });
  }, []);

  useEffect(() => {
    // 1. Fetch categories
    categoryService.getCategories().then(cats => {
      setCategories(cats || []);
      if (cats && cats.length > 0) {
        setCategoryId(current => current || cats[0].id);
      }
    }).catch(err => toast.error(extractErrorMessage(err, 'Không thể tải danh mục')));
  }, []);

  useEffect(() => {
    // 2. Fetch service if in edit mode
    if (serviceId) {
      loadServiceData(serviceId);
    }
  }, [serviceId, loadServiceData]);

  // Save Service (Create or Update)
  const handleSaveService = async (andSubmit = false): Promise<VendorServiceDto | null> => {
    if (!nameVi.trim()) {
      toast.warning('Vui lòng nhập tên dịch vụ (Tiếng Việt).');
      return null;
    }
    if (!categoryId) {
      toast.warning('Vui lòng chọn danh mục dịch vụ.');
      return null;
    }
    if (price <= 0) {
      toast.warning('Đơn giá niêm yết phải lớn hơn 0 đ.');
      return null;
    }
    if (durationMinutes <= 0) {
      toast.warning('Thời lượng dịch vụ phải lớn hơn 0 phút.');
      return null;
    }
    if (waiverRequired && !waiverContent.trim() && !waiverContentEn.trim()) {
      toast.warning('Khi yêu cầu miễn trừ trách nhiệm, vui lòng nhập nội dung cam kết an toàn (Waiver).');
      return null;
    }
    if (weatherSensitive && (minWindKmh == null || maxWaveM == null)) {
      toast.warning('Dịch vụ nhạy cảm thời tiết biển bắt buộc phải cấu hình cả Giới hạn gió (km/h) và Độ cao sóng (m).');
      return null;
    }

    setIsSaving(true);
    try {
      let savedService: VendorServiceDto;

      if (!currentServiceId) {
        // Create new DRAFT service
        const createPayload: CreateServiceRequest = {
          categoryId,
          name: nameVi.trim(),
          nameEn: nameEn.trim() || undefined,
          description: descriptionVi.trim() || undefined,
          descriptionEn: descriptionEn.trim() || undefined,
          price,
          durationMinutes,
          capacityPerSlot,
          locationName: locationName.trim() || undefined,
          address: address.trim() || undefined,
          latitude: latitude != null ? latitude : undefined,
          longitude: longitude != null ? longitude : undefined,
          waiverRequired,
          waiverContent: waiverContent.trim() || undefined,
          waiverContentEn: waiverContentEn.trim() || undefined,
          weatherSensitive,
          minWindKmh: minWindKmh != null ? minWindKmh : undefined,
          maxWaveM: maxWaveM != null ? maxWaveM : undefined,
        };
        savedService = await vendorService.createService(createPayload);
        setCurrentServiceId(savedService.id);
        setServiceStatus(savedService.status);
        showToast('Đã tạo dịch vụ thành công ở trạng thái Bản nháp (DRAFT). Bạn có thể thêm ảnh và lựa chọn bán.');
      } else {
        // Update existing service
        const updatePayload: UpdateServiceRequest = {
          categoryId,
          name: nameVi.trim(),
          nameEn: nameEn.trim() || undefined,
          description: descriptionVi.trim() || undefined,
          descriptionEn: descriptionEn.trim() || undefined,
          price,
          durationMinutes,
          capacityPerSlot,
          locationName: locationName.trim() || undefined,
          address: address.trim() || undefined,
          latitude: latitude != null ? latitude : undefined,
          longitude: longitude != null ? longitude : undefined,
          waiverRequired,
          waiverContent: waiverContent.trim() || undefined,
          waiverContentEn: waiverContentEn.trim() || undefined,
          weatherSensitive,
          minWindKmh: minWindKmh != null ? minWindKmh : undefined,
          maxWaveM: maxWaveM != null ? maxWaveM : undefined,
        };
        savedService = await vendorService.updateService(currentServiceId, updatePayload);
        setServiceStatus(savedService.status);
        showToast('Đã cập nhật thông tin dịch vụ thành công.');
      }

      if (andSubmit) {
        if (savedService.status !== 'DRAFT' && savedService.status !== 'REJECTED') {
          toast.info(
            savedService.status === 'PENDING_REVIEW'
              ? 'Dịch vụ đang ở trạng thái Chờ duyệt bởi Quản trị viên. Không cần gửi duyệt lại.'
              : `Dịch vụ đang ở trạng thái ${savedService.status}.`,
            'Thông báo'
          );
          return savedService;
        }
        if (!images || images.length === 0) {
          toast.warning('Dịch vụ cần ít nhất 1 ảnh để gửi duyệt. Vui lòng chuyển sang tab "Ảnh dịch vụ" để tải ảnh lên.');
          setActiveTab('IMAGES');
          return savedService;
        }
        const submitted = await vendorService.submitForReview(savedService.id);
        setServiceStatus(submitted.status);
        toast.success('Đã gửi duyệt dịch vụ thành công tới Quản trị viên!');
        setTimeout(() => navigate('/vendor/services'), 1500);
      }

      return savedService;
    } catch (err: unknown) {
      console.error('[ServiceForm] Lỗi lưu dịch vụ:', err);
      const msg = extractErrorMessage(err, 'Lưu thông tin dịch vụ thất bại');
      toast.error(msg);
      return null;
    } finally {
      setIsSaving(false);
    }
  };

  // Tab 2: Upload Image
  const handleImageFileChange = async (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    if (!currentServiceId) {
      toast.warning('Vui lòng lưu thông tin dịch vụ (Lưu nháp) trước khi tải ảnh lên.');
      return;
    }

    if (images.length >= 10) {
      toast.warning('Mỗi dịch vụ chỉ được tải tối đa 10 hình ảnh.');
      return;
    }

    setIsUploadingImage(true);
    try {
      const uploaded = await vendorService.uploadImage(currentServiceId, file);
      setImages(prev => [...prev, uploaded]);
      showToast('Đã tải ảnh lên thành công.');
      // Reset input
      e.target.value = '';
    } catch (err: unknown) {
      console.error('[ServiceForm] Lỗi tải ảnh:', err);
      toast.error(extractErrorMessage(err, "Tải ảnh lên thất bại"));
    } finally {
      setIsUploadingImage(false);
    }
  };

  // Tab 2: Delete Image
  const handleDeleteImage = async (imageId: string) => {
    if (!currentServiceId) return;
    if (!window.confirm('Bạn có chắc muốn xóa ảnh này?')) return;
    try {
      await vendorService.deleteImage(currentServiceId, imageId);
      setImages(prev => prev.filter(img => img.id !== imageId));
      showToast('Đã xóa ảnh thành công.');
    } catch (err: unknown) {
      console.error('[ServiceForm] Lỗi xóa ảnh:', err);
      const msg = extractErrorMessage(err, 'Không thể xóa ảnh');
      toast.error(msg);
    }
  };

  // Tab 2: Move Image
  const handleMoveImage = async (index: number, direction: 'UP' | 'DOWN') => {
    if (!currentServiceId) return;
    const targetIndex = direction === 'UP' ? index - 1 : index + 1;
    if (targetIndex < 0 || targetIndex >= images.length) return;

    const newArr = [...images];
    const temp = newArr[index];
    newArr[index] = newArr[targetIndex];
    newArr[targetIndex] = temp;

    setImages(newArr);
    try {
      const reordered = await vendorService.reorderImages(
        currentServiceId,
        newArr.map(img => img.id)
      );
      setImages(reordered || newArr);
      showToast('Đã cập nhật thứ tự ảnh thành công.');
    } catch (err: unknown) {
      console.error('[ServiceForm] Lỗi sắp xếp ảnh:', err);
      // Revert if API failed
      setImages(images);
    }
  };

  // Tab 3: Create Option
  const handleCreateOption = async () => {
    if (!currentServiceId) {
      toast.warning('Vui lòng lưu dịch vụ trước khi tạo lựa chọn bán.');
      return;
    }
    if (!optName.trim()) {
      toast.warning('Vui lòng nhập tên gói/lựa chọn.');
      return;
    }
    if (optPrice <= 0) {
      toast.warning('Đơn giá phải lớn hơn 0 đ.');
      return;
    }
    if (optType === 'PRIVATE' && (!optMaxPax || optMaxPax <= 0)) {
      toast.warning('Gói riêng tư (PRIVATE) bắt buộc phải cấu hình số khách tối đa (maxPaxPerPackage > 0).');
      return;
    }

    try {
      const newOption = await vendorService.createOption(currentServiceId, {
        name: optName.trim(),
        optionType: optType,
        pricingUnit: optPricingUnit,
        price: optPrice,
        maxPaxPerPackage: optType === 'PRIVATE' ? optMaxPax : null,
        benefits: optBenefits.trim() || null,
      });
      setOptions(prev => [...prev, newOption]);
      showToast(`Đã thêm lựa chọn bán "${newOption.name}" thành công.`);
      setIsAddOptionModalOpen(false);
      // Reset form
      setOptName('');
      setOptPrice(300000);
      setOptMaxPax(null);
      setOptBenefits('');
    } catch (err: unknown) {
      console.error('[ServiceForm] Lỗi tạo option:', err);
      const msg = extractErrorMessage(err, 'Không thể tạo lựa chọn');
      toast.error(msg);
    }
  };

  // Tab 4: Upload Safety Document
  const handleSafetyDocChange = async (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    if (!currentServiceId) {
      toast.warning('Vui lòng lưu thông tin dịch vụ trước khi tải chứng từ an toàn.');
      return;
    }

    setIsUploadingDoc(true);
    try {
      const doc = await vendorService.uploadSafetyDocument(currentServiceId, file);
      setSafetyDocs(prev => [...prev, doc]);
      showToast('Đã tải lên chứng từ an toàn dịch vụ thành công.');
      e.target.value = '';
    } catch (err: unknown) {
      console.error('[ServiceForm] Lỗi tải chứng từ an toàn:', err);
      toast.error(extractErrorMessage(err, "Tải chứng từ an toàn thất bại"));
    } finally {
      setIsUploadingDoc(false);
    }
  };

  return (
    <div className="w-full bg-background flex-1">
      <div className="flex flex-col w-full">
        {/* Header Bar */}
        <div className="w-full bg-white border-b border-slate-200 px-6 py-4 flex flex-wrap items-center justify-between gap-4 shadow-xs sticky top-0 z-20">
          <div className="flex flex-col">
            <nav className="flex items-center gap-1.5 text-xs text-slate-500 mb-1">
              <span 
                onClick={() => navigate('/vendor/services')} 
                className="hover:text-primary transition-colors cursor-pointer"
              >
                Dịch vụ &amp; Trải nghiệm
              </span>
              <span className="material-symbols-outlined text-[14px]">chevron_right</span>
              <span className="text-black font-semibold">
                {isEditMode ? 'Chỉnh sửa dịch vụ' : 'Soạn dịch vụ mới'}
              </span>
            </nav>
            <div className="flex items-center gap-2">
              <h1 className="text-lg md:text-xl font-bold text-black tracking-tight">
                {nameVi || 'Chưa đặt tên dịch vụ'}
              </h1>
              {serviceStatus === 'PUBLISHED' && (
                <span className="px-2 py-0.5 rounded text-[11px] font-semibold bg-emerald-50 text-emerald-800 border border-emerald-300">
                  Đang mở bán
                </span>
              )}
              {serviceStatus === 'PENDING_REVIEW' && (
                <span className="px-2 py-0.5 rounded text-[11px] font-semibold bg-amber-50 text-amber-800 border border-amber-300">
                  Chờ duyệt
                </span>
              )}
              {serviceStatus === 'APPROVED' && (
                <span className="px-2 py-0.5 rounded text-[11px] font-semibold bg-sky-50 text-sky-800 border border-sky-300">
                  Đã duyệt
                </span>
              )}
              {serviceStatus === 'REJECTED' && (
                <span className="px-2 py-0.5 rounded text-[11px] font-semibold bg-rose-50 text-rose-800 border border-rose-300">
                  Bị từ chối
                </span>
              )}
              {serviceStatus === 'DRAFT' && (
                <span className="px-2 py-0.5 rounded text-[11px] font-medium bg-slate-100 text-slate-800 border border-slate-200">
                  Bản nháp (DRAFT)
                </span>
              )}
              {serviceStatus === 'PAUSED' && (
                <span className="px-2 py-0.5 rounded text-[11px] font-medium bg-amber-50 text-amber-800 border border-amber-300">
                  Tạm ngưng
                </span>
              )}
            </div>
          </div>

          {/* Action Buttons */}
          <div className="flex items-center gap-2">
            <button 
              onClick={() => handleSaveService(false)}
              disabled={isSaving}
              className="px-3.5 py-1.5 rounded-lg border border-slate-300 bg-white hover:bg-slate-50 text-black text-xs font-semibold transition-colors flex items-center gap-1.5 disabled:opacity-50" 
              type="button"
            >
              <span className="material-symbols-outlined text-[16px]">save</span>
              <span>{isSaving ? 'Đang lưu...' : 'Lưu nháp'}</span>
            </button>

            <button 
              onClick={() => setPreviewOpen(true)}
              className="px-3.5 py-1.5 rounded-lg border border-slate-300 bg-white hover:bg-slate-50 text-slate-700 text-xs font-semibold transition-colors flex items-center gap-1.5" 
              type="button"
            >
              <span className="material-symbols-outlined text-[16px]">visibility</span>
              <span>Xem trước</span>
            </button>

            {serviceStatus === 'PENDING_REVIEW' ? (
              <div className="px-3 py-1.5 rounded-lg bg-amber-50 border border-amber-300 text-amber-800 text-xs font-semibold flex items-center gap-1.5">
                <span className="material-symbols-outlined text-[16px] text-amber-600">hourglass_top</span>
                <span>Đang chờ duyệt</span>
              </div>
            ) : serviceStatus === 'PUBLISHED' ? (
              <button 
                onClick={() => handleSaveService(false)}
                disabled={isSaving}
                className="px-4 py-1.5 rounded-lg bg-primary hover:bg-primary/90 text-on-primary text-xs font-bold transition-colors flex items-center gap-1.5 shadow-xs disabled:opacity-50" 
                type="button"
              >
                <span className="material-symbols-outlined text-[16px]">save</span>
                <span>{isSaving ? 'Đang lưu...' : 'Lưu cập nhật'}</span>
              </button>
            ) : (
              <button 
                onClick={() => handleSaveService(true)}
                disabled={isSaving}
                className="px-4 py-1.5 rounded-lg bg-primary hover:bg-primary/90 text-on-primary text-xs font-bold transition-colors flex items-center gap-1.5 shadow-xs disabled:opacity-50" 
                type="button"
              >
                <span className="material-symbols-outlined text-[16px]">send</span>
                <span>Gửi duyệt</span>
              </button>
            )}
          </div>
        </div>

        {/* Rejection Alert if REJECTED */}
        {serviceStatus === 'REJECTED' && rejectionReason && (
          <div className="mx-6 mt-4 p-4 rounded-xl bg-rose-50 border border-rose-300 flex items-start gap-3">
            <span className="material-symbols-outlined text-rose-600 mt-0.5">report_problem</span>
            <div>
              <h4 className="font-bold text-xs text-rose-800">Dịch vụ bị Quản trị viên từ chối duyệt</h4>
              <p className="text-xs text-rose-700 mt-0.5">Lý do: <span className="font-semibold">{rejectionReason}</span></p>
              <p className="text-[11px] text-rose-600 mt-1">Vui lòng cập nhật thông tin/chứng từ hợp lệ theo yêu cầu và bấm &quot;Gửi duyệt&quot; lại.</p>
            </div>
          </div>
        )}

        {/* 4 Mandatory Tabs Navigation */}
        <div className="px-6 pt-4">
          <div className="flex border-b border-slate-200 gap-6 text-sm font-semibold">
            <button
              onClick={() => setActiveTab('CONTENT_SAFETY')}
              className={`pb-3 flex items-center gap-1.5 border-b-2 transition-all ${
                activeTab === 'CONTENT_SAFETY'
                  ? 'border-primary text-primary'
                  : 'border-transparent text-slate-500 hover:text-black'
              }`}
              type="button"
            >
              <span className="material-symbols-outlined text-[18px]">description</span>
              <span>1. Nội dung &amp; An toàn</span>
            </button>

            <button
              onClick={() => setActiveTab('IMAGES')}
              className={`pb-3 flex items-center gap-1.5 border-b-2 transition-all ${
                activeTab === 'IMAGES'
                  ? 'border-primary text-primary'
                  : 'border-transparent text-slate-500 hover:text-black'
              }`}
              type="button"
            >
              <span className="material-symbols-outlined text-[18px]">photo_library</span>
              <span>2. Ảnh dịch vụ</span>
              <span className="px-1.5 py-0.2 rounded-full text-[10px] bg-slate-100 text-slate-600">
                {images.length}
              </span>
            </button>

            <button
              onClick={() => setActiveTab('OPTIONS')}
              className={`pb-3 flex items-center gap-1.5 border-b-2 transition-all ${
                activeTab === 'OPTIONS'
                  ? 'border-primary text-primary'
                  : 'border-transparent text-slate-500 hover:text-black'
              }`}
              type="button"
            >
              <span className="material-symbols-outlined text-[18px]">sell</span>
              <span>3. Lựa chọn bán (Options)</span>
              <span className="px-1.5 py-0.2 rounded-full text-[10px] bg-slate-100 text-slate-600">
                {options.length}
              </span>
            </button>

            <button
              onClick={() => setActiveTab('SAFETY_DOCS')}
              className={`pb-3 flex items-center gap-1.5 border-b-2 transition-all ${
                activeTab === 'SAFETY_DOCS'
                  ? 'border-primary text-primary'
                  : 'border-transparent text-slate-500 hover:text-black'
              }`}
              type="button"
            >
              <span className="material-symbols-outlined text-[18px]">verified_user</span>
              <span>4. Chứng từ an toàn</span>
              <span className="px-1.5 py-0.2 rounded-full text-[10px] bg-slate-100 text-slate-600">
                {safetyDocs.length}
              </span>
            </button>
          </div>
        </div>

        {/* Tab Content Panes */}
        <div className="p-6">
          {isLoading ? (
            <div className="w-full bg-white rounded-xl border border-slate-200 p-8 text-center text-slate-500">
              <span className="material-symbols-outlined text-3xl animate-spin">sync</span>
              <p className="mt-2 text-xs">Đang nạp dữ liệu dịch vụ...</p>
            </div>
          ) : (
            <>
              {/* TAB 1: CONTENT & SAFETY */}
              {activeTab === 'CONTENT_SAFETY' && (
                <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
                  {/* Left Column: Form Fields */}
                  <div className="lg:col-span-8 space-y-6">
                    {/* Basic Info */}
                    <div className="bg-white rounded-xl border border-slate-200 p-5 space-y-4 shadow-xs">
                      <h2 className="text-sm font-bold text-black border-b border-slate-100 pb-2">
                        Thông tin cơ bản
                      </h2>

                      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <div className="md:col-span-2">
                          <label className="block text-xs font-semibold text-slate-700 mb-1">
                            Tên dịch vụ (Tiếng Việt) <span className="text-rose-500">*</span>
                          </label>
                          <input 
                            type="text"
                            value={nameVi}
                            onChange={(e) => setNameVi(e.target.value)}
                            className="w-full px-3 py-2 border border-slate-300 rounded-lg text-xs text-black focus:ring-1 focus:ring-primary focus:outline-none"
                            placeholder="VD: Chèo SUP đón bình minh biển Mỹ Khê"
                          />
                        </div>

                        <div className="md:col-span-2">
                          <label className="block text-xs font-semibold text-slate-700 mb-1">
                            Tên dịch vụ (Tiếng Anh - Tùy chọn)
                          </label>
                          <input 
                            type="text"
                            value={nameEn}
                            onChange={(e) => setNameEn(e.target.value)}
                            className="w-full px-3 py-2 border border-slate-300 rounded-lg text-xs text-black focus:ring-1 focus:ring-primary focus:outline-none"
                            placeholder="VD: Sunrise Stand-up Paddleboarding at My Khe Beach"
                          />
                        </div>

                        <div>
                          <label className="block text-xs font-semibold text-slate-700 mb-1">
                            Danh mục thể thao biển <span className="text-rose-500">*</span>
                          </label>
                          <select 
                            value={categoryId}
                            onChange={(e) => setCategoryId(e.target.value)}
                            className="w-full px-3 py-2 border border-slate-300 rounded-lg text-xs text-black focus:ring-1 focus:ring-primary focus:outline-none bg-white cursor-pointer"
                          >
                            <option value="">-- Chọn danh mục --</option>
                            {categories.map(cat => (
                              <option key={cat.id} value={cat.id}>{cat.name}</option>
                            ))}
                          </select>
                        </div>

                        <div>
                          <label className="block text-xs font-semibold text-slate-700 mb-1">
                            Đơn giá niêm yết chuẩn (VNĐ) <span className="text-rose-500">*</span>
                          </label>
                          <input 
                            type="number"
                            min={1000}
                            step={1000}
                            value={price}
                            onChange={(e) => setPrice(Number(e.target.value))}
                            className="w-full px-3 py-2 border border-slate-300 rounded-lg text-xs font-mono font-bold text-black focus:ring-1 focus:ring-primary focus:outline-none"
                          />
                        </div>

                        <div>
                          <label className="block text-xs font-semibold text-slate-700 mb-1">
                            Thời lượng mỗi ca (phút) <span className="text-rose-500">*</span>
                          </label>
                          <input 
                            type="number"
                            min={10}
                            value={durationMinutes}
                            onChange={(e) => setDurationMinutes(Number(e.target.value))}
                            className="w-full px-3 py-2 border border-slate-300 rounded-lg text-xs text-black focus:ring-1 focus:ring-primary focus:outline-none"
                          />
                        </div>

                        <div>
                          <label className="block text-xs font-semibold text-slate-700 mb-1">
                            Sức chứa định danh mỗi ca (khách) <span className="text-rose-500">*</span>
                          </label>
                          <input 
                            type="number"
                            min={1}
                            value={capacityPerSlot}
                            onChange={(e) => setCapacityPerSlot(Number(e.target.value))}
                            className="w-full px-3 py-2 border border-slate-300 rounded-lg text-xs text-black focus:ring-1 focus:ring-primary focus:outline-none"
                          />
                        </div>

                        <div className="md:col-span-2">
                          <label className="block text-xs font-semibold text-slate-700 mb-1">
                            Mô tả chi tiết dịch vụ (Tiếng Việt)
                          </label>
                          <textarea 
                            rows={3}
                            value={descriptionVi}
                            onChange={(e) => setDescriptionVi(e.target.value)}
                            className="w-full px-3 py-2 border border-slate-300 rounded-lg text-xs text-black focus:ring-1 focus:ring-primary focus:outline-none"
                            placeholder="Mô tả lộ trình, trang thiết bị cung cấp, kinh nghiệm cần có..."
                          />
                        </div>
                      </div>
                    </div>

                    {/* Location Info */}
                    <div className="bg-white rounded-xl border border-slate-200 p-5 space-y-4 shadow-xs">
                      <h2 className="text-sm font-bold text-black border-b border-slate-100 pb-2">
                        Vị trí &amp; Địa điểm tập trung
                      </h2>
                      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <div>
                          <label className="block text-xs font-semibold text-slate-700 mb-1">
                            Tên bến / Điểm xuất phát
                          </label>
                          <input 
                            type="text"
                            value={locationName}
                            onChange={(e) => setLocationName(e.target.value)}
                            className="w-full px-3 py-2 border border-slate-300 rounded-lg text-xs text-black focus:ring-1 focus:ring-primary focus:outline-none"
                          />
                        </div>

                        <div>
                          <label className="block text-xs font-semibold text-slate-700 mb-1">
                            Địa chỉ chi tiết
                          </label>
                          <input 
                            type="text"
                            value={address}
                            onChange={(e) => setAddress(e.target.value)}
                            className="w-full px-3 py-2 border border-slate-300 rounded-lg text-xs text-black focus:ring-1 focus:ring-primary focus:outline-none"
                          />
                        </div>

                        <div>
                          <label className="block text-xs font-semibold text-slate-700 mb-1">
                            Vĩ độ (Latitude)
                          </label>
                          <input 
                            type="number"
                            step="0.0001"
                            value={latitude ?? ''}
                            onChange={(e) => setLatitude(e.target.value ? Number(e.target.value) : null)}
                            className="w-full px-3 py-2 border border-slate-300 rounded-lg text-xs font-mono text-black focus:ring-1 focus:ring-primary focus:outline-none"
                          />
                        </div>

                        <div>
                          <label className="block text-xs font-semibold text-slate-700 mb-1">
                            Kinh độ (Longitude)
                          </label>
                          <input 
                            type="number"
                            step="0.0001"
                            value={longitude ?? ''}
                            onChange={(e) => setLongitude(e.target.value ? Number(e.target.value) : null)}
                            className="w-full px-3 py-2 border border-slate-300 rounded-lg text-xs font-mono text-black focus:ring-1 focus:ring-primary focus:outline-none"
                          />
                        </div>
                      </div>
                    </div>

                    {/* Maritime Safety & Waiver Policy */}
                    <div className="bg-white rounded-xl border border-slate-200 p-5 space-y-4 shadow-xs">
                      <h2 className="text-sm font-bold text-black border-b border-slate-100 pb-2">
                        Chính sách an toàn biển &amp; Miễn trừ trách nhiệm (Waiver)
                      </h2>

                      {/* Waiver Toggle & Content */}
                      <div className="space-y-3">
                        <label className="flex items-center gap-2 cursor-pointer">
                          <input 
                            type="checkbox"
                            checked={waiverRequired}
                            onChange={(e) => setWaiverRequired(e.target.checked)}
                            className="rounded border-slate-300 text-primary focus:ring-primary"
                          />
                          <span className="text-xs font-bold text-black">
                            Bắt buộc du khách ký cam kết an toàn điện tử (Waiver) trước khi thanh toán
                          </span>
                        </label>

                        {waiverRequired && (
                          <div className="space-y-2 pl-6">
                            <label className="block text-xs font-semibold text-slate-700">
                              Nội dung điều khoản miễn trừ trách nhiệm <span className="text-rose-500">*</span>
                            </label>
                            <textarea 
                              rows={3}
                              value={waiverContent}
                              onChange={(e) => setWaiverContent(e.target.value)}
                              className="w-full px-3 py-2 border border-slate-300 rounded-lg text-xs text-black focus:ring-1 focus:ring-primary focus:outline-none"
                              placeholder="Nhập các điều khoản bắt buộc khách phải chấp nhận..."
                            />
                          </div>
                        )}
                      </div>

                      {/* Weather Sensitivity */}
                      <div className="space-y-3 pt-3 border-t border-slate-100">
                        <label className="flex items-center gap-2 cursor-pointer">
                          <input 
                            type="checkbox"
                            checked={weatherSensitive}
                            onChange={(e) => setWeatherSensitive(e.target.checked)}
                            className="rounded border-slate-300 text-primary focus:ring-primary"
                          />
                          <span className="text-xs font-bold text-black">
                            Dịch vụ nhạy cảm với điều kiện thời tiết biển (Cần cấu hình ngưỡng an toàn)
                          </span>
                        </label>

                        {weatherSensitive && (
                          <div className="grid grid-cols-1 md:grid-cols-2 gap-4 pl-6">
                            <div>
                              <label className="block text-xs font-semibold text-slate-700 mb-1">
                                Giới hạn gió tối đa (km/h) <span className="text-rose-500">*</span>
                              </label>
                              <input 
                                type="number"
                                min={1}
                                value={minWindKmh ?? ''}
                                onChange={(e) => setMinWindKmh(e.target.value ? Number(e.target.value) : null)}
                                className="w-full px-3 py-2 border border-slate-300 rounded-lg text-xs text-black focus:ring-1 focus:ring-primary focus:outline-none"
                              />
                            </div>

                            <div>
                              <label className="block text-xs font-semibold text-slate-700 mb-1">
                                Độ cao sóng tối đa (mét) <span className="text-rose-500">*</span>
                              </label>
                              <input 
                                type="number"
                                step="0.1"
                                min={0.1}
                                value={maxWaveM ?? ''}
                                onChange={(e) => setMaxWaveM(e.target.value ? Number(e.target.value) : null)}
                                className="w-full px-3 py-2 border border-slate-300 rounded-lg text-xs text-black focus:ring-1 focus:ring-primary focus:outline-none"
                              />
                            </div>
                          </div>
                        )}
                      </div>
                    </div>
                  </div>

                  {/* Right Column: Quick Status & Next Tabs Guidance */}
                  <div className="lg:col-span-4 space-y-4">
                    <div className="bg-white rounded-xl border border-slate-200 p-4 shadow-xs space-y-3">
                      <h3 className="font-bold text-xs text-black">Tiến độ thiết lập dịch vụ</h3>
                      <div className="space-y-2 text-xs">
                        <div className="flex items-center justify-between">
                          <span className="text-slate-600">1. Thông tin cơ bản &amp; an toàn:</span>
                          <span className="text-emerald-600 font-bold flex items-center gap-1">
                            <span className="material-symbols-outlined text-[14px]">check_circle</span> Xong
                          </span>
                        </div>
                        <div className="flex items-center justify-between">
                          <span className="text-slate-600">2. Hình ảnh minh họa:</span>
                          <span className={`font-bold flex items-center gap-1 ${images.length > 0 ? 'text-emerald-600' : 'text-amber-600'}`}>
                            {images.length > 0 ? `${images.length} ảnh` : 'Chưa có ảnh'}
                          </span>
                        </div>
                        <div className="flex items-center justify-between">
                          <span className="text-slate-600">3. Gói / Lựa chọn bán:</span>
                          <span className="text-slate-700 font-bold">{options.length} gói</span>
                        </div>
                        <div className="flex items-center justify-between">
                          <span className="text-slate-600">4. Chứng chỉ an toàn:</span>
                          <span className="text-slate-700 font-bold">{safetyDocs.length} tài liệu</span>
                        </div>
                      </div>

                      <div className="pt-2 border-t border-slate-100">
                        <p className="text-[11px] text-slate-500">
                          Mẹo: Nhấn nút &quot;Lưu nháp&quot; để tạo mã dịch vụ trước khi sang các tab tải ảnh và tạo gói bán.
                        </p>
                      </div>
                    </div>
                  </div>
                </div>
              )}

              {/* TAB 2: IMAGES */}
              {activeTab === 'IMAGES' && (
                <div className="bg-white rounded-xl border border-slate-200 p-5 space-y-5 shadow-xs">
                  <div className="flex items-center justify-between border-b border-slate-100 pb-3">
                    <div>
                      <h2 className="text-sm font-bold text-black">Thư viện ảnh dịch vụ (Tối đa 10 ảnh)</h2>
                      <p className="text-xs text-slate-500 mt-0.5">
                        Ảnh chất lượng cao giúp tăng tỉ lệ khách đặt tour biển. Dịch vụ bắt buộc có ít nhất 1 ảnh để gửi duyệt.
                      </p>
                    </div>

                    <div>
                      <label className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-primary text-on-primary text-xs font-semibold hover:bg-primary/90 transition-colors cursor-pointer shadow-xs">
                        <span className="material-symbols-outlined text-[16px]">upload</span>
                        <span>{isUploadingImage ? 'Đang tải lên...' : 'Tải ảnh mới'}</span>
                        <input 
                          type="file" 
                          accept="image/jpeg,image/png,image/webp" 
                          onChange={handleImageFileChange}
                          disabled={isUploadingImage || images.length >= 10}
                          className="hidden" 
                        />
                      </label>
                    </div>
                  </div>

                  {images.length === 0 ? (
                    <div className="py-12 text-center text-slate-500">
                      <span className="material-symbols-outlined text-4xl text-slate-400">add_photo_alternate</span>
                      <p className="mt-2 text-xs font-semibold text-slate-700">Chưa có ảnh nào được tải lên</p>
                      <p className="text-[11px] text-slate-500 mt-0.5">Vui lòng tải lên ít nhất 1 ảnh đại diện để hoàn thiện dịch vụ.</p>
                    </div>
                  ) : (
                    <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
                      {images.map((img, idx) => (
                        <div key={img.id} className="group relative rounded-xl border border-slate-200 overflow-hidden bg-slate-50 flex flex-col">
                          <div className="aspect-4/3 w-full overflow-hidden bg-slate-100 relative">
                            <img src={img.url} alt="Service" className="w-full h-full object-cover" />
                            {idx === 0 && (
                              <span className="absolute top-2 left-2 px-2 py-0.5 bg-primary text-on-primary text-[10px] font-bold rounded-md shadow-xs">
                                Ảnh đại diện
                              </span>
                            )}
                          </div>
                          <div className="p-2 flex items-center justify-between bg-white text-xs text-slate-700">
                            <span className="text-[11px] font-semibold">Thứ tự: {idx + 1}</span>
                            <div className="flex items-center gap-1">
                              <button
                                onClick={() => handleMoveImage(idx, 'UP')}
                                disabled={idx === 0}
                                className="p-1 rounded hover:bg-slate-100 disabled:opacity-30"
                                title="Đưa lên"
                                type="button"
                              >
                                <span className="material-symbols-outlined text-[16px]">arrow_upward</span>
                              </button>
                              <button
                                onClick={() => handleMoveImage(idx, 'DOWN')}
                                disabled={idx === images.length - 1}
                                className="p-1 rounded hover:bg-slate-100 disabled:opacity-30"
                                title="Hạ xuống"
                                type="button"
                              >
                                <span className="material-symbols-outlined text-[16px]">arrow_downward</span>
                              </button>
                              <button
                                onClick={() => handleDeleteImage(img.id)}
                                className="p-1 rounded text-rose-600 hover:bg-rose-50"
                                title="Xóa ảnh"
                                type="button"
                              >
                                <span className="material-symbols-outlined text-[16px]">delete</span>
                              </button>
                            </div>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )}

              {/* TAB 3: OPTIONS */}
              {activeTab === 'OPTIONS' && (
                <div className="bg-white rounded-xl border border-slate-200 p-5 space-y-5 shadow-xs">
                  <div className="flex items-center justify-between border-b border-slate-100 pb-3">
                    <div>
                      <h2 className="text-sm font-bold text-black">Các gói &amp; Lựa chọn bán (Pricing Options)</h2>
                      <p className="text-xs text-slate-500 mt-0.5">
                        Tùy chọn ghép đoàn (SHARED tính theo người) hoặc bao trọn cano/thuyền (PRIVATE tính theo gói).
                      </p>
                    </div>

                    <button 
                      onClick={() => setIsAddOptionModalOpen(true)}
                      className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-primary text-on-primary text-xs font-semibold hover:bg-primary/90 transition-colors shadow-xs"
                      type="button"
                    >
                      <span className="material-symbols-outlined text-[16px]">add</span>
                      <span>Thêm lựa chọn bán</span>
                    </button>
                  </div>

                  {options.length === 0 ? (
                    <div className="py-12 text-center text-slate-500">
                      <span className="material-symbols-outlined text-4xl text-slate-400">sell</span>
                      <p className="mt-2 text-xs font-semibold text-slate-700">Chưa có lựa chọn bổ sung</p>
                      <p className="text-[11px] text-slate-500 mt-0.5">Dịch vụ sẽ tự động áp dụng đơn giá niêm yết chuẩn ở Tab 1.</p>
                    </div>
                  ) : (
                    <div className="overflow-x-auto">
                      <table className="w-full text-left border-collapse text-xs">
                        <thead>
                          <tr className="bg-slate-50 text-slate-700 font-bold border-b border-slate-200">
                            <th className="py-2.5 px-3">Tên gói / Lựa chọn</th>
                            <th className="py-2.5 px-3">Hình thức</th>
                            <th className="py-2.5 px-3">Đơn vị tính</th>
                            <th className="py-2.5 px-3 text-right">Đơn giá</th>
                            <th className="py-2.5 px-3 text-center">Tối đa khách/gói</th>
                            <th className="py-2.5 px-3">Quyền lợi</th>
                            <th className="py-2.5 px-3 text-center">Trạng thái</th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-slate-100">
                          {options.map(opt => (
                            <tr key={opt.id} className="hover:bg-slate-50">
                              <td className="py-2.5 px-3 font-bold text-black">{opt.name}</td>
                              <td className="py-2.5 px-3">
                                <span className={`px-2 py-0.5 rounded text-[10px] font-semibold ${
                                  opt.optionType === 'PRIVATE' ? 'bg-purple-50 text-purple-700 border border-purple-200' : 'bg-blue-50 text-blue-700 border border-blue-200'
                                }`}>
                                  {opt.optionType === 'PRIVATE' ? 'Bao trọn gói (Private)' : 'Ghép đoàn (Shared)'}
                                </span>
                              </td>
                              <td className="py-2.5 px-3">
                                {opt.pricingUnit === 'PER_PACKAGE' ? 'Theo gói trọn gói' : 'Theo đầu người'}
                              </td>
                              <td className="py-2.5 px-3 text-right font-mono font-bold text-black">
                                {opt.price.toLocaleString('vi-VN')} đ
                              </td>
                              <td className="py-2.5 px-3 text-center">
                                {opt.maxPaxPerPackage ? `${opt.maxPaxPerPackage} khách` : '-'}
                              </td>
                              <td className="py-2.5 px-3 text-slate-600 max-w-xs truncate" title={opt.benefits || ''}>
                                {opt.benefits || '-'}
                              </td>
                              <td className="py-2.5 px-3 text-center">
                                <span className="px-2 py-0.5 rounded text-[10px] font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
                                  {opt.status === 'ACTIVE' ? 'Đang mở bán' : 'Ngưng bán'}
                                </span>
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  )}
                </div>
              )}

              {/* TAB 4: SAFETY DOCUMENTS */}
              {activeTab === 'SAFETY_DOCS' && (
                <div className="bg-white rounded-xl border border-slate-200 p-5 space-y-5 shadow-xs">
                  <div className="flex items-center justify-between border-b border-slate-100 pb-3">
                    <div>
                      <h2 className="text-sm font-bold text-black">Chứng chỉ &amp; Giấy phép an toàn dịch vụ</h2>
                      <p className="text-xs text-slate-500 mt-0.5">
                        Tải lên chứng nhận kiểm định cano, phao cứu sinh, bằng thuyền trưởng hoặc chứng chỉ lặn an toàn.
                      </p>
                    </div>

                    <div>
                      <label className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-primary text-on-primary text-xs font-semibold hover:bg-primary/90 transition-colors cursor-pointer shadow-xs">
                        <span className="material-symbols-outlined text-[16px]">upload_file</span>
                        <span>{isUploadingDoc ? 'Đang tải...' : 'Tải chứng chỉ (PDF/Ảnh)'}</span>
                        <input 
                          type="file" 
                          accept="application/pdf,image/jpeg,image/png" 
                          onChange={handleSafetyDocChange}
                          disabled={isUploadingDoc}
                          className="hidden" 
                        />
                      </label>
                    </div>
                  </div>

                  {safetyDocs.length === 0 ? (
                    <div className="py-12 text-center text-slate-500">
                      <span className="material-symbols-outlined text-4xl text-slate-400">verified_user</span>
                      <p className="mt-2 text-xs font-semibold text-slate-700">Chưa có chứng chỉ an toàn nào</p>
                      <p className="text-[11px] text-slate-500 mt-0.5">Đính kèm chứng chỉ kiểm định an toàn để hồ sơ dịch vụ được duyệt nhanh chóng.</p>
                    </div>
                  ) : (
                    <div className="space-y-3">
                      {safetyDocs.map(doc => (
                        <div key={doc.id} className="p-3 rounded-lg border border-slate-200 bg-slate-50 flex items-center justify-between gap-3 text-xs">
                          <div className="flex items-center gap-3">
                            <span className="material-symbols-outlined text-2xl text-slate-500">description</span>
                            <div>
                              <a href={doc.fileUrl} target="_blank" rel="noreferrer" className="font-bold text-primary hover:underline">
                                Xem chứng từ đã tải lên
                              </a>
                              <p className="text-[11px] text-slate-500">Ngày nộp: {new Date(doc.createdAt).toLocaleDateString('vi-VN')}</p>
                            </div>
                          </div>

                          <div className="flex items-center gap-2">
                            {doc.status === 'APPROVED' && (
                              <span className="px-2 py-0.5 rounded text-[11px] font-semibold bg-emerald-50 text-emerald-800 border border-emerald-300">
                                Đã duyệt
                              </span>
                            )}
                            {doc.status === 'PENDING' && (
                              <span className="px-2 py-0.5 rounded text-[11px] font-semibold bg-amber-50 text-amber-800 border border-amber-300">
                                Chờ kiểm tra
                              </span>
                            )}
                            {doc.status === 'REJECTED' && (
                              <span className="px-2 py-0.5 rounded text-[11px] font-semibold bg-rose-50 text-rose-800 border border-rose-300">
                                Bị từ chối
                              </span>
                            )}
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )}
            </>
          )}
        </div>

        {/* Modal: Add Option */}
        {isAddOptionModalOpen && createPortal(
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-xs p-4">
            <div className="bg-white rounded-xl shadow-lg border border-slate-200 w-full max-w-md p-5 space-y-4">
              <div className="flex items-center justify-between border-b border-slate-100 pb-2">
                <h3 className="font-bold text-sm text-black">Thêm lựa chọn bán / Gói dịch vụ</h3>
                <button onClick={() => setIsAddOptionModalOpen(false)} className="text-slate-400 hover:text-black">
                  <span className="material-symbols-outlined text-[18px]">close</span>
                </button>
              </div>

              <div className="space-y-3 text-xs">
                <div>
                  <label className="block font-semibold text-slate-700 mb-1">Tên gói / Lựa chọn:</label>
                  <input 
                    type="text"
                    value={optName}
                    onChange={(e) => setOptName(e.target.value)}
                    className="w-full px-3 py-1.5 border border-slate-300 rounded-lg text-black focus:ring-1 focus:ring-primary focus:outline-none"
                    placeholder="VD: Bao trọn cano riêng kèm flycam"
                  />
                </div>

                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block font-semibold text-slate-700 mb-1">Hình thức:</label>
                    <select
                      value={optType}
                      onChange={(e) => {
                        const newType = e.target.value as OptionType;
                        setOptType(newType);
                        if (newType === 'PRIVATE') {
                          setOptPricingUnit('PER_PACKAGE');
                          if (!optMaxPax) setOptMaxPax(6);
                        } else {
                          setOptPricingUnit('PER_PERSON');
                          setOptMaxPax(null);
                        }
                      }}
                      className="w-full px-2.5 py-1.5 border border-slate-300 rounded-lg text-black focus:ring-1 focus:ring-primary focus:outline-none bg-white cursor-pointer"
                    >
                      <option value="SHARED">Ghép đoàn (SHARED)</option>
                      <option value="PRIVATE">Bao trọn gói (PRIVATE)</option>
                    </select>
                  </div>

                  <div>
                    <label className="block font-semibold text-slate-700 mb-1">Đơn vị tính:</label>
                    <input 
                      type="text"
                      disabled
                      value={optPricingUnit === 'PER_PACKAGE' ? 'Theo gói (PER_PACKAGE)' : 'Theo người (PER_PERSON)'}
                      className="w-full px-2.5 py-1.5 border border-slate-200 bg-slate-100 rounded-lg text-slate-600 font-medium"
                    />
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block font-semibold text-slate-700 mb-1">Đơn giá (VNĐ):</label>
                    <input 
                      type="number"
                      min={1000}
                      step={1000}
                      value={optPrice}
                      onChange={(e) => setOptPrice(Number(e.target.value))}
                      className="w-full px-3 py-1.5 border border-slate-300 rounded-lg font-mono font-bold text-black focus:ring-1 focus:ring-primary focus:outline-none"
                    />
                  </div>

                  {optType === 'PRIVATE' && (
                    <div>
                      <label className="block font-semibold text-slate-700 mb-1">Tối đa khách / gói:</label>
                      <input 
                        type="number"
                        min={1}
                        value={optMaxPax ?? ''}
                        onChange={(e) => setOptMaxPax(e.target.value ? Number(e.target.value) : null)}
                        className="w-full px-3 py-1.5 border border-slate-300 rounded-lg text-black focus:ring-1 focus:ring-primary focus:outline-none"
                      />
                    </div>
                  )}
                </div>

                <div>
                  <label className="block font-semibold text-slate-700 mb-1">Quyền lợi bổ sung:</label>
                  <textarea 
                    rows={2}
                    value={optBenefits}
                    onChange={(e) => setOptBenefits(e.target.value)}
                    className="w-full px-3 py-1.5 border border-slate-300 rounded-lg text-black focus:ring-1 focus:ring-primary focus:outline-none"
                    placeholder="VD: Bao gồm nước suối, khăn lạnh, chụp ảnh miễn phí..."
                  />
                </div>
              </div>

              <div className="flex items-center justify-end gap-2 pt-2 border-t border-slate-100">
                <button
                  onClick={() => setIsAddOptionModalOpen(false)}
                  className="px-3 py-1.5 rounded-lg border border-slate-200 text-xs font-medium text-slate-700 hover:bg-slate-50"
                  type="button"
                >
                  Hủy
                </button>
                <button
                  onClick={handleCreateOption}
                  className="px-3.5 py-1.5 rounded-lg bg-primary text-on-primary text-xs font-bold hover:bg-primary/90 transition-colors"
                  type="button"
                >
                  Lưu lựa chọn
                </button>
              </div>
            </div>
          </div>,
          document.body
        )}

        {/* Modal: Preview */}
        {previewOpen && createPortal(
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-xs p-4">
            <div className="bg-white rounded-xl shadow-lg border border-slate-200 w-full max-w-lg p-5 space-y-4 max-h-[90vh] overflow-y-auto">
              <div className="flex items-center justify-between border-b border-slate-100 pb-2">
                <h3 className="font-bold text-sm text-black">Bản xem trước dịch vụ</h3>
                <button onClick={() => setPreviewOpen(false)} className="text-slate-400 hover:text-black">
                  <span className="material-symbols-outlined text-[18px]">close</span>
                </button>
              </div>

              <div className="space-y-3 text-xs text-slate-700">
                <div className="aspect-16/9 rounded-lg overflow-hidden bg-slate-100">
                  <img 
                    src={images[0]?.url || 'https://images.unsplash.com/photo-1544551763-46a013bb70d5?auto=format&fit=crop&w=600&q=80'} 
                    alt="Preview" 
                    className="w-full h-full object-cover" 
                  />
                </div>
                <h4 className="font-bold text-base text-black">{nameVi || 'Tên dịch vụ'}</h4>
                <p className="text-slate-600">{descriptionVi || 'Chưa có mô tả chi tiết.'}</p>
                <div className="grid grid-cols-2 gap-2 p-2 bg-slate-50 rounded-lg">
                  <div>Đơn vị niêm yết: <span className="font-mono font-bold text-black">{price.toLocaleString('vi-VN')} đ</span></div>
                  <div>Thời lượng: <span className="font-semibold text-black">{durationMinutes} phút</span></div>
                  <div>Sức chứa: <span className="font-semibold text-black">{capacityPerSlot} người/ca</span></div>
                  <div>Địa điểm: <span className="font-semibold text-black">{locationName}</span></div>
                </div>
              </div>

              <div className="flex justify-end pt-2 border-t border-slate-100">
                <button
                  onClick={() => setPreviewOpen(false)}
                  className="px-4 py-1.5 bg-slate-800 text-white rounded-lg text-xs font-bold"
                  type="button"
                >
                  Đóng
                </button>
              </div>
            </div>
          </div>,
          document.body
        )}

        
      </div>
    </div>
  );
}
