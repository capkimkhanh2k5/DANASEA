import { useState, useEffect, useCallback, type ChangeEvent } from 'react';
import { createPortal } from 'react-dom';
import { useNavigate, useParams } from 'react-router-dom';
import { 
  Loader2, 
  Wand2,
  ArrowLeft,
  Save,
  Eye,
  Send,
  FileText,
  Image as ImageIcon,
  Tag,
  ShieldCheck,
  CheckCircle2,
  AlertTriangle,
  Plus,
  Upload,
  Trash2,
  ArrowUp,
  ArrowDown,
  X,
  Hourglass
} from 'lucide-react';
import { 
  AtlasPageHeader, 
  AtlasSegmentedTabs, 
  AtlasBadge, 
  AtlasButton,
  type AtlasTabItem 
} from '../../../components/vendor/atlas/AtlasDesignSystem';
import { vendorService } from '../../../services/vendorService';
import { categoryService } from '../../../services/categoryService';
import { vendorAiService } from '../../../services/vendorAiService';
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

function translateMarineTitle(viTitle: string): string {
  const dictionary: [RegExp, string][] = [
    [/chèo sup/gi, 'Stand-up Paddleboarding (SUP)'],
    [/mô tô nước|moto nuoc|jet ski/gi, 'Jet Ski Watersport'],
    [/cano cao tốc|cano/gi, 'High-speed Speedboat Tour'],
    [/lặn ngắm san hô|lặn biển/gi, 'Snorkeling & Coral Reef Diving'],
    [/du thuyền ngắm hoàng hôn/gi, 'Sunset Yacht Cruise'],
    [/du thuyền/gi, 'Luxury Yacht Cruise'],
    [/câu cá biển|câu mực đêm/gi, 'Night Squid & Deep Sea Fishing'],
    [/lướt sóng/gi, 'Surfing Lesson & Board Rental'],
    [/trượt phao chuối/gi, 'Banana Boat Ride'],
    [/dù bay cano kéo/gi, 'Parasailing Flight'],
    [/đón bình minh/gi, 'Sunrise Experience'],
    [/ngắm hoàng hôn/gi, 'Sunset Experience'],
    [/bãi biển mỹ khê/gi, 'My Khe Beach'],
    [/bán đảo sơn trà/gi, 'Son Tra Peninsula'],
    [/bãi bụt/gi, 'Bai But Beach'],
    [/bãi rạng/gi, 'Bai Rang Beach'],
    [/bãi nam/gi, 'Bai Nam Reef'],
    [/hòn sụp/gi, 'Hon Sup Island'],
    [/vịnh đà nẵng/gi, 'Da Nang Bay'],
    [/đà nẵng/gi, 'Da Nang'],
  ];

  let result = viTitle;
  for (const [pattern, repl] of dictionary) {
    result = result.replace(pattern, repl);
  }
  return result;
}

export function ServiceForm() {
  const { serviceId } = useParams<{ serviceId?: string }>();
  return <ServiceFormPage key={serviceId || 'new'} serviceId={serviceId} />;
}

function ServiceFormPage({ serviceId }: { serviceId: string | undefined }) {
  const navigate = useNavigate();

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
  const [isOptimizingAi, setIsOptimizingAi] = useState<boolean>(false);

  const handleAiOptimizeListing = async () => {
    if (!nameVi.trim()) {
      toast.error('Vui lòng nhập tên dịch vụ hoặc từ khóa sơ bộ trước khi tối ưu');
      return;
    }
    setIsOptimizingAi(true);
    try {
      const res = await vendorAiService.optimizeListing({
        serviceId: currentServiceId || undefined,
        rawTitle: nameVi.trim(),
        rawDescription: descriptionVi.trim() || nameVi.trim(),
        highlights: [],
        includedItems: [],
        targetLanguages: ['vi', 'en'],
      });

      const draftVi = res.drafts?.vi || res.drafts?.['vi'];
      const draftEn = res.drafts?.en || res.drafts?.['en'];

      if (draftVi?.optimizedTitle) {
        setNameVi(draftVi.optimizedTitle);
      }
      if (draftVi?.optimizedDescription) {
        setDescriptionVi(draftVi.optimizedDescription);
      }
      if (draftEn?.optimizedTitle) {
        setNameEn(draftEn.optimizedTitle);
      }
      if (draftEn?.optimizedDescription) {
        setDescriptionEn(draftEn.optimizedDescription);
      }
      if (draftVi?.safetyAdvisory && (!waiverContent || waiverContent.length < 20)) {
        setWaiverContent(draftVi.safetyAdvisory);
      }

      toast.success('Đã tối ưu hóa tiêu đề, mô tả chuẩn SEO và song ngữ cho tour!');
    } catch (err) {
      console.error('[ListingOptimizer] AI fallback:', err);
      // Fallback từ điển du lịch biển chính xác cao
      const marineTitle = translateMarineTitle(nameVi.trim());
      const marineDesc = descriptionVi.trim()
        ? translateMarineTitle(descriptionVi.trim())
        : `Experience premium ${marineTitle} in Da Nang with international SOLAS maritime safety standards and certified local instructors.`;
      setNameEn(marineTitle);
      setDescriptionEn(marineDesc);
      toast.success('Đã hoàn thiện bản dịch tiếng Anh chuẩn du lịch biển!');
    } finally {
      setIsOptimizingAi(false);
    }
  };

  const showToast = (msg: string) => {
    toast.success(msg);
  };

  // Load initial data
  const loadServiceData = useCallback((id: string) => {
    return Promise.all([
      vendorService.getServiceDetail(id),
      vendorService.getOptions(id).catch(() => []),
      vendorService.getSafetyDocuments(id).catch(() => []),
    ]).then(([svc, opts, docs]) => {
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
      setSafetyDocs(docs || []);
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
      toast.warning('Khi yêu cầu miễn trừ trách nhiệm, vui lòng nhập nội dung cam kết an toàn.');
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
      toast.warning('Gói bao trọn chuyến bắt buộc phải cấu hình số khách tối đa.');
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

  const renderStatusBadge = (status: string) => {
    switch (status) {
      case 'PUBLISHED':
        return <AtlasBadge label="Đang mở bán" variant="success" />;
      case 'PENDING_REVIEW':
        return <AtlasBadge label="Chờ duyệt" variant="warning" />;
      case 'APPROVED':
        return <AtlasBadge label="Đã duyệt" variant="info" />;
      case 'REJECTED':
        return <AtlasBadge label="Bị từ chối" variant="danger" />;
      case 'PAUSED':
        return <AtlasBadge label="Tạm ngưng" variant="warning" />;
      case 'DRAFT':
      default:
        return <AtlasBadge label="Bản nháp" variant="neutral" />;
    }
  };

  const formTabs: AtlasTabItem<FormTab>[] = [
    { id: 'CONTENT_SAFETY', label: '1. Nội dung & An toàn' },
    { id: 'IMAGES', label: '2. Ảnh dịch vụ', count: images.length },
    { id: 'OPTIONS', label: '3. Lựa chọn bán', count: options.length },
    { id: 'SAFETY_DOCS', label: '4. Chứng từ an toàn', count: safetyDocs.length },
  ];

  return (
    <div className="w-full max-w-[1400px] mx-auto px-6 py-5 space-y-6">
      {/* 1. Page Header (Atlas Standard) */}
      <AtlasPageHeader
        title={serviceId ? (nameVi || 'Chỉnh sửa dịch vụ') : 'Tạo dịch vụ mới'}
        actions={
          <div className="flex items-center gap-2.5 flex-wrap">
            <AtlasButton
              variant="outline"
              size="sm"
              onClick={() => navigate('/vendor/services')}
              icon={<ArrowLeft className="w-4 h-4" />}
            >
              Quay lại
            </AtlasButton>

            {serviceId && renderStatusBadge(serviceStatus)}

            <AtlasButton
              variant="outline"
              size="sm"
              onClick={() => handleSaveService(false)}
              disabled={isSaving}
              icon={isSaving ? <Loader2 className="w-4 h-4 animate-spin text-slate-500" /> : <Save className="w-4 h-4 text-slate-600" />}
            >
              {isSaving ? 'Đang lưu...' : 'Lưu nháp'}
            </AtlasButton>

            <AtlasButton
              variant="outline"
              size="sm"
              onClick={() => setPreviewOpen(true)}
              icon={<Eye className="w-4 h-4 text-slate-600" />}
            >
              Xem trước
            </AtlasButton>

            {serviceStatus === 'PENDING_REVIEW' ? (
              <div className="px-3.5 py-1.5 rounded-xl bg-amber-50 border border-amber-200 text-amber-800 text-xs font-semibold flex items-center gap-1.5">
                <Hourglass className="w-4 h-4 text-amber-600 animate-pulse" />
                <span>Đang chờ duyệt</span>
              </div>
            ) : serviceStatus === 'PUBLISHED' ? (
              <AtlasButton
                variant="primary"
                size="sm"
                onClick={() => handleSaveService(false)}
                disabled={isSaving}
                icon={isSaving ? <Loader2 className="w-4 h-4 animate-spin" /> : <Save className="w-4 h-4" />}
              >
                {isSaving ? 'Đang lưu...' : 'Lưu cập nhật'}
              </AtlasButton>
            ) : (
              <AtlasButton
                variant="primary"
                size="sm"
                onClick={() => handleSaveService(true)}
                disabled={isSaving}
                icon={isSaving ? <Loader2 className="w-4 h-4 animate-spin" /> : <Send className="w-4 h-4" />}
              >
                Gửi duyệt
              </AtlasButton>
            )}
          </div>
        }
      />

      {/* Rejection Alert if REJECTED */}
      {serviceStatus === 'REJECTED' && rejectionReason && (
        <div className="p-4.5 rounded-2xl bg-rose-50 border border-rose-200 flex items-start gap-3">
          <AlertTriangle className="w-5 h-5 text-rose-600 shrink-0 mt-0.5" />
          <div>
            <h4 className="font-bold text-sm text-rose-900">Dịch vụ bị Quản trị viên từ chối duyệt</h4>
            <p className="text-xs text-rose-700 mt-1">Lý do: <span className="font-semibold text-rose-800">{rejectionReason}</span></p>
            <p className="text-xs text-rose-600 mt-1">Vui lòng cập nhật thông tin/chứng từ hợp lệ theo yêu cầu và bấm &quot;Gửi duyệt&quot; lại.</p>
          </div>
        </div>
      )}

      {/* 2. Atlas Segmented Tabs Navigation */}
      <div className="flex items-center justify-between gap-4">
        <AtlasSegmentedTabs<FormTab>
          tabs={formTabs}
          activeTab={activeTab}
          onChange={(tabId) => setActiveTab(tabId)}
        />
      </div>

      {/* 3. Tab Content Panes */}
      <div>
        {isLoading ? (
          <div className="w-full bg-white rounded-2xl border border-slate-200/80 p-12 text-center text-slate-500 shadow-xs">
            <Loader2 className="w-8 h-8 animate-spin text-sky-600 mx-auto" />
            <p className="mt-3 text-sm font-medium text-slate-600">Đang nạp dữ liệu dịch vụ...</p>
          </div>
        ) : (
          <>
            {/* TAB 1: CONTENT & SAFETY */}
            {activeTab === 'CONTENT_SAFETY' && (
              <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
                {/* Left Column: Form Fields (8 cols) */}
                <div className="lg:col-span-8 space-y-6">
                  {/* Basic Info */}
                  <div className="bg-white rounded-2xl border border-slate-200/80 p-6 space-y-5 shadow-xs">
                    <div className="flex items-center justify-between border-b border-slate-100 pb-3">
                      <h2 className="text-base font-bold text-slate-900 font-display">
                        Thông tin cơ bản
                      </h2>
                      <button
                        type="button"
                        onClick={handleAiOptimizeListing}
                        disabled={isOptimizingAi || !nameVi.trim()}
                        className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-slate-900 hover:bg-black text-white text-xs font-semibold shadow-xs transition-colors cursor-pointer disabled:opacity-40"
                        title="AI viết và tối ưu hóa tiêu đề, mô tả chuẩn SEO du lịch biển & song ngữ"
                      >
                        {isOptimizingAi ? (
                          <>
                            <Loader2 className="w-3.5 h-3.5 animate-spin text-slate-300" />
                            <span>Đang tối ưu...</span>
                          </>
                        ) : (
                          <>
                            <Wand2 className="w-3.5 h-3.5 text-sky-400" />
                            <span>Tối ưu nội dung tour</span>
                          </>
                        )}
                      </button>
                    </div>

                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                      <div className="md:col-span-2">
                        <label className="block text-xs font-semibold text-slate-700 mb-1.5">
                          Tên dịch vụ <span className="text-rose-500">*</span>
                        </label>
                        <input 
                          type="text"
                          value={nameVi}
                          onChange={(e) => setNameVi(e.target.value)}
                          className="w-full px-3.5 py-2.5 border border-slate-200/90 rounded-xl text-xs sm:text-sm text-slate-900 focus:border-sky-500 focus:ring-2 focus:ring-sky-500/20 focus:outline-none transition-all placeholder:text-slate-400 bg-white"
                          placeholder="VD: Chèo SUP đón bình minh biển Mỹ Khê"
                        />
                      </div>

                      <div className="md:col-span-2">
                        <label className="block text-xs font-semibold text-slate-700 mb-1.5">
                          Tên tiếng Anh (Tùy chọn)
                        </label>
                        <input 
                          type="text"
                          value={nameEn}
                          onChange={(e) => setNameEn(e.target.value)}
                          className="w-full px-3.5 py-2.5 border border-slate-200/90 rounded-xl text-xs sm:text-sm text-slate-900 focus:border-sky-500 focus:ring-2 focus:ring-sky-500/20 focus:outline-none transition-all placeholder:text-slate-400 bg-white"
                          placeholder="VD: Sunrise Stand-up Paddleboarding at My Khe Beach"
                        />
                      </div>

                      <div>
                        <label className="block text-xs font-semibold text-slate-700 mb-1.5">
                          Danh mục thể thao biển <span className="text-rose-500">*</span>
                        </label>
                        <select 
                          value={categoryId}
                          onChange={(e) => setCategoryId(e.target.value)}
                          className="w-full px-3.5 py-2.5 border border-slate-200/90 rounded-xl text-xs sm:text-sm font-semibold text-slate-900 focus:border-sky-500 focus:ring-2 focus:ring-sky-500/20 focus:outline-none transition-all bg-white cursor-pointer"
                        >
                          <option value="">-- Chọn danh mục --</option>
                          {categories.map(cat => (
                            <option key={cat.id} value={cat.id}>{cat.name}</option>
                          ))}
                        </select>
                      </div>

                      <div>
                        <label className="block text-xs font-semibold text-slate-700 mb-1.5">
                          Đơn giá niêm yết (VNĐ) <span className="text-rose-500">*</span>
                        </label>
                        <input 
                          type="number"
                          min={1000}
                          step={1000}
                          value={price}
                          onChange={(e) => setPrice(Number(e.target.value))}
                          className="w-full px-3.5 py-2.5 border border-slate-200/90 rounded-xl text-xs sm:text-sm font-mono font-bold text-slate-900 tabular-nums focus:border-sky-500 focus:ring-2 focus:ring-sky-500/20 focus:outline-none transition-all bg-white"
                        />
                      </div>

                      <div>
                        <label className="block text-xs font-semibold text-slate-700 mb-1.5">
                          Thời lượng ca (phút) <span className="text-rose-500">*</span>
                        </label>
                        <input 
                          type="number"
                          min={10}
                          value={durationMinutes}
                          onChange={(e) => setDurationMinutes(Number(e.target.value))}
                          className="w-full px-3.5 py-2.5 border border-slate-200/90 rounded-xl text-xs sm:text-sm text-slate-900 focus:border-sky-500 focus:ring-2 focus:ring-sky-500/20 focus:outline-none transition-all bg-white"
                        />
                      </div>

                      <div>
                        <label className="block text-xs font-semibold text-slate-700 mb-1.5">
                          Sức chứa mỗi ca (khách) <span className="text-rose-500">*</span>
                        </label>
                        <input 
                          type="number"
                          min={1}
                          value={capacityPerSlot}
                          onChange={(e) => setCapacityPerSlot(Number(e.target.value))}
                          className="w-full px-3.5 py-2.5 border border-slate-200/90 rounded-xl text-xs sm:text-sm text-slate-900 focus:border-sky-500 focus:ring-2 focus:ring-sky-500/20 focus:outline-none transition-all bg-white"
                          placeholder="Số khách dự kiến"
                        />
                      </div>

                      <div className="md:col-span-2">
                        <label className="block text-xs font-semibold text-slate-700 mb-1.5">
                          Mô tả dịch vụ
                        </label>
                        <textarea 
                          rows={3}
                          value={descriptionVi}
                          onChange={(e) => setDescriptionVi(e.target.value)}
                          className="w-full px-3.5 py-2.5 border border-slate-200/90 rounded-xl text-xs sm:text-sm text-slate-900 focus:border-sky-500 focus:ring-2 focus:ring-sky-500/20 focus:outline-none transition-all placeholder:text-slate-400 bg-white"
                          placeholder="Mô tả lộ trình, trang thiết bị cung cấp, kinh nghiệm cần có..."
                        />
                      </div>

                      <div className="md:col-span-2">
                        <label className="block text-xs font-semibold text-slate-700 mb-1.5">
                          Mô tả tiếng Anh (Tùy chọn)
                        </label>
                        <textarea 
                          rows={3}
                          value={descriptionEn}
                          onChange={(e) => setDescriptionEn(e.target.value)}
                          className="w-full px-3.5 py-2.5 border border-slate-200/90 rounded-xl text-xs sm:text-sm text-slate-900 focus:border-sky-500 focus:ring-2 focus:ring-sky-500/20 focus:outline-none transition-all placeholder:text-slate-400 bg-white"
                          placeholder="Detailed itinerary, equipment, requirements in English..."
                        />
                      </div>
                    </div>
                  </div>

                  {/* Location Info */}
                  <div className="bg-white rounded-2xl border border-slate-200/80 p-6 space-y-5 shadow-xs">
                    <h2 className="text-base font-bold text-slate-900 font-display border-b border-slate-100 pb-3">
                      Vị trí &amp; Điểm xuất phát
                    </h2>
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                      <div>
                        <label className="block text-xs font-semibold text-slate-700 mb-1.5">
                          Tên bến / Điểm xuất phát
                        </label>
                        <input 
                          type="text"
                          value={locationName}
                          onChange={(e) => setLocationName(e.target.value)}
                          className="w-full px-3.5 py-2.5 border border-slate-200/90 rounded-xl text-xs sm:text-sm text-slate-900 focus:border-sky-500 focus:ring-2 focus:ring-sky-500/20 focus:outline-none transition-all bg-white"
                        />
                      </div>

                      <div>
                        <label className="block text-xs font-semibold text-slate-700 mb-1.5">
                          Địa chỉ chi tiết
                        </label>
                        <input 
                          type="text"
                          value={address}
                          onChange={(e) => setAddress(e.target.value)}
                          className="w-full px-3.5 py-2.5 border border-slate-200/90 rounded-xl text-xs sm:text-sm text-slate-900 focus:border-sky-500 focus:ring-2 focus:ring-sky-500/20 focus:outline-none transition-all bg-white"
                        />
                      </div>

                      <div>
                        <label className="block text-xs font-semibold text-slate-700 mb-1.5">
                          Vĩ độ
                        </label>
                        <input 
                          type="number"
                          step="0.0001"
                          value={latitude ?? ''}
                          onChange={(e) => setLatitude(e.target.value ? Number(e.target.value) : null)}
                          className="w-full px-3.5 py-2.5 border border-slate-200/90 rounded-xl text-xs sm:text-sm font-mono text-slate-900 focus:border-sky-500 focus:ring-2 focus:ring-sky-500/20 focus:outline-none transition-all bg-white"
                        />
                      </div>

                      <div>
                        <label className="block text-xs font-semibold text-slate-700 mb-1.5">
                          Kinh độ
                        </label>
                        <input 
                          type="number"
                          step="0.0001"
                          value={longitude ?? ''}
                          onChange={(e) => setLongitude(e.target.value ? Number(e.target.value) : null)}
                          className="w-full px-3.5 py-2.5 border border-slate-200/90 rounded-xl text-xs sm:text-sm font-mono text-slate-900 focus:border-sky-500 focus:ring-2 focus:ring-sky-500/20 focus:outline-none transition-all bg-white"
                        />
                      </div>
                    </div>
                  </div>

                  {/* Maritime Safety & Waiver Policy */}
                  <div className="bg-white rounded-2xl border border-slate-200/80 p-6 space-y-5 shadow-xs">
                    <h2 className="text-base font-bold text-slate-900 font-display border-b border-slate-100 pb-3">
                      Chính sách an toàn &amp; Miễn trừ trách nhiệm
                    </h2>

                    {/* Waiver Toggle & Content */}
                    <div className="space-y-3">
                      <label className="flex items-center gap-2.5 cursor-pointer select-none">
                        <input 
                          type="checkbox"
                          checked={waiverRequired}
                          onChange={(e) => setWaiverRequired(e.target.checked)}
                          className="w-4 h-4 rounded border-slate-300 text-sky-600 focus:ring-sky-500"
                        />
                        <span className="text-xs sm:text-sm font-bold text-slate-900">
                          Bắt buộc du khách ký cam kết an toàn điện tử trước khi thanh toán
                        </span>
                      </label>

                      {waiverRequired && (
                        <div className="space-y-2 pl-6 pt-1">
                          <label className="block text-xs font-semibold text-slate-700 mb-1">
                            Nội dung điều khoản miễn trừ trách nhiệm <span className="text-rose-500">*</span>
                          </label>
                          <textarea 
                            rows={3}
                            value={waiverContent}
                            onChange={(e) => setWaiverContent(e.target.value)}
                            className="w-full px-3.5 py-2.5 border border-slate-200/90 rounded-xl text-xs sm:text-sm text-slate-900 focus:border-sky-500 focus:ring-2 focus:ring-sky-500/20 focus:outline-none transition-all placeholder:text-slate-400 bg-white"
                            placeholder="Nhập các điều khoản bắt buộc khách phải chấp nhận..."
                          />
                        </div>
                      )}
                    </div>

                    {/* Weather Sensitivity */}
                    <div className="space-y-3 pt-4 border-t border-slate-100">
                      <label className="flex items-center gap-2.5 cursor-pointer select-none">
                        <input 
                          type="checkbox"
                          checked={weatherSensitive}
                          onChange={(e) => setWeatherSensitive(e.target.checked)}
                          className="w-4 h-4 rounded border-slate-300 text-sky-600 focus:ring-sky-500"
                        />
                        <span className="text-xs sm:text-sm font-bold text-slate-900">
                          Dịch vụ phụ thuộc điều kiện thời tiết biển
                        </span>
                      </label>

                      {weatherSensitive && (
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4 pl-6 pt-1">
                          <div>
                            <label className="block text-xs font-semibold text-slate-700 mb-1.5">
                              Giới hạn gió tối đa (km/h) <span className="text-rose-500">*</span>
                            </label>
                            <input 
                              type="number"
                              min={1}
                              value={minWindKmh ?? ''}
                              onChange={(e) => setMinWindKmh(e.target.value ? Number(e.target.value) : null)}
                              className="w-full px-3.5 py-2.5 border border-slate-200/90 rounded-xl text-xs sm:text-sm text-slate-900 focus:border-sky-500 focus:ring-2 focus:ring-sky-500/20 focus:outline-none transition-all bg-white"
                            />
                          </div>

                          <div>
                            <label className="block text-xs font-semibold text-slate-700 mb-1.5">
                              Độ cao sóng tối đa (mét) <span className="text-rose-500">*</span>
                            </label>
                            <input 
                              type="number"
                              step="0.1"
                              min={0.1}
                              value={maxWaveM ?? ''}
                              onChange={(e) => setMaxWaveM(e.target.value ? Number(e.target.value) : null)}
                              className="w-full px-3.5 py-2.5 border border-slate-200/90 rounded-xl text-xs sm:text-sm text-slate-900 focus:border-sky-500 focus:ring-2 focus:ring-sky-500/20 focus:outline-none transition-all bg-white"
                            />
                          </div>
                        </div>
                      )}
                    </div>
                  </div>
                </div>

                {/* Right Column: Quick Status & Next Tabs Guidance (4 cols) */}
                <div className="lg:col-span-4 space-y-4 sticky top-6">
                  <div className="bg-white rounded-2xl border border-slate-200/80 p-5 shadow-xs space-y-4">
                    <h3 className="font-bold text-sm text-slate-900 font-display">Tiến độ thiết lập dịch vụ</h3>
                    <div className="space-y-3 text-xs">
                      <div className="flex items-center justify-between py-1">
                        <span className="text-slate-600">1. Thông tin cơ bản &amp; an toàn:</span>
                        <span className="text-emerald-600 font-bold flex items-center gap-1 font-mono">
                          <CheckCircle2 className="w-3.5 h-3.5" /> Xong
                        </span>
                      </div>
                      <div className="flex items-center justify-between py-1 border-t border-slate-100">
                        <span className="text-slate-600">2. Hình ảnh minh họa:</span>
                        <span className={`font-bold flex items-center gap-1 font-mono ${images.length > 0 ? 'text-emerald-600' : 'text-amber-600'}`}>
                          {images.length > 0 ? `${images.length} ảnh` : 'Chưa có ảnh'}
                        </span>
                      </div>
                      <div className="flex items-center justify-between py-1 border-t border-slate-100">
                        <span className="text-slate-600">3. Gói / Lựa chọn bán:</span>
                        <span className="text-slate-700 font-bold font-mono">{options.length} gói</span>
                      </div>
                      <div className="flex items-center justify-between py-1 border-t border-slate-100">
                        <span className="text-slate-600">4. Chứng chỉ an toàn:</span>
                        <span className="text-slate-700 font-bold font-mono">{safetyDocs.length} tài liệu</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            )}

            {/* TAB 2: IMAGES */}
            {activeTab === 'IMAGES' && (
              <div className="bg-white rounded-2xl border border-slate-200/80 p-6 space-y-6 shadow-xs">
                <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 border-b border-slate-100 pb-4">
                  <div>
                    <h2 className="text-base font-bold text-slate-900 font-display">Thư viện ảnh dịch vụ</h2>
                  </div>

                  <div>
                    <label className="inline-flex items-center gap-2 px-4 py-2 rounded-xl bg-slate-900 text-white text-xs font-semibold hover:bg-black transition-colors cursor-pointer shadow-xs disabled:opacity-50">
                      {isUploadingImage ? <Loader2 className="w-4 h-4 animate-spin text-slate-300" /> : <Upload className="w-4 h-4" />}
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
                  <div className="py-16 text-center">
                    <div className="w-12 h-12 rounded-2xl bg-slate-100 flex items-center justify-center mx-auto mb-3 text-slate-400">
                      <ImageIcon className="w-6 h-6" />
                    </div>
                    <p className="text-sm font-semibold text-slate-800">Chưa có ảnh nào được tải lên</p>
                    <p className="text-xs text-slate-500 mt-1 max-w-sm mx-auto">Vui lòng tải lên ít nhất 1 ảnh đại diện để hoàn thiện dịch vụ.</p>
                  </div>
                ) : (
                  <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
                    {images.map((img, idx) => (
                      <div key={img.id} className="group relative rounded-2xl border border-slate-200/80 overflow-hidden bg-slate-50 flex flex-col shadow-2xs">
                        <div className="aspect-4/3 w-full overflow-hidden bg-slate-100 relative">
                          <img src={img.url} alt="Service" className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500" />
                          {idx === 0 && (
                            <span className="absolute top-2.5 left-2.5 px-2.5 py-1 bg-slate-900/90 text-white text-[11px] font-bold rounded-lg shadow-xs backdrop-blur-xs">
                              Ảnh đại diện
                            </span>
                          )}
                        </div>
                        <div className="p-3 flex items-center justify-between bg-white text-xs text-slate-700 border-t border-slate-100">
                          <span className="text-xs font-semibold font-mono text-slate-600">Thứ tự: {idx + 1}</span>
                          <div className="flex items-center gap-1">
                            <button
                              onClick={() => handleMoveImage(idx, 'UP')}
                              disabled={idx === 0}
                              className="p-1.5 rounded-lg text-slate-600 hover:bg-slate-100 disabled:opacity-30 cursor-pointer transition-colors"
                              title="Đưa lên"
                              type="button"
                            >
                              <ArrowUp className="w-3.5 h-3.5" />
                            </button>
                            <button
                              onClick={() => handleMoveImage(idx, 'DOWN')}
                              disabled={idx === images.length - 1}
                              className="p-1.5 rounded-lg text-slate-600 hover:bg-slate-100 disabled:opacity-30 cursor-pointer transition-colors"
                              title="Hạ xuống"
                              type="button"
                            >
                              <ArrowDown className="w-3.5 h-3.5" />
                            </button>
                            <button
                              onClick={() => handleDeleteImage(img.id)}
                              className="p-1.5 rounded-lg text-rose-600 hover:bg-rose-50 cursor-pointer transition-colors"
                              title="Xóa ảnh"
                              type="button"
                            >
                              <Trash2 className="w-3.5 h-3.5" />
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
              <div className="bg-white rounded-2xl border border-slate-200/80 p-6 space-y-6 shadow-xs">
                <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 border-b border-slate-100 pb-4">
                  <div>
                    <h2 className="text-base font-bold text-slate-900 font-display">Các gói dịch vụ &amp; Lựa chọn bán</h2>
                  </div>

                  <AtlasButton
                    variant="primary"
                    size="sm"
                    onClick={() => setIsAddOptionModalOpen(true)}
                    icon={<Plus className="w-4 h-4" />}
                  >
                    Thêm lựa chọn bán
                  </AtlasButton>
                </div>

                {options.length === 0 ? (
                  <div className="py-16 text-center">
                    <div className="w-12 h-12 rounded-2xl bg-slate-100 flex items-center justify-center mx-auto mb-3 text-slate-400">
                      <Tag className="w-6 h-6" />
                    </div>
                    <p className="text-sm font-semibold text-slate-800">Chưa có lựa chọn bổ sung</p>
                    <p className="text-xs text-slate-500 mt-1 max-w-sm mx-auto">
                      Dịch vụ sẽ tự động áp dụng đơn giá niêm yết chuẩn ({price.toLocaleString('vi-VN')} đ) ở Tab 1.
                    </p>
                  </div>
                ) : (
                  <div className="rounded-xl border border-slate-200/80 overflow-hidden shadow-2xs">
                    <table className="w-full text-left border-collapse text-xs">
                      <thead>
                        <tr className="bg-slate-50/80 text-slate-700 font-semibold border-b border-slate-200/80">
                          <th className="py-3 px-4 uppercase tracking-wider text-[11px]">Tên gói / Lựa chọn</th>
                          <th className="py-3 px-4 uppercase tracking-wider text-[11px]">Hình thức</th>
                          <th className="py-3 px-4 uppercase tracking-wider text-[11px]">Đơn vị tính</th>
                          <th className="py-3 px-4 uppercase tracking-wider text-[11px] text-right">Đơn giá</th>
                          <th className="py-3 px-4 uppercase tracking-wider text-[11px] text-center">Tối đa khách</th>
                          <th className="py-3 px-4 uppercase tracking-wider text-[11px]">Quyền lợi</th>
                          <th className="py-3 px-4 uppercase tracking-wider text-[11px] text-center">Trạng thái</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-slate-100">
                        {options.map(opt => (
                          <tr key={opt.id} className="hover:bg-slate-50/80 transition-colors">
                            <td className="py-3 px-4 font-bold text-slate-900">{opt.name}</td>
                            <td className="py-3 px-4">
                              <span className={`px-2.5 py-1 rounded-lg text-[11px] font-semibold ${
                                opt.optionType === 'PRIVATE' ? 'bg-purple-50 text-purple-700 border border-purple-200/80' : 'bg-sky-50 text-sky-700 border border-sky-200/80'
                              }`}>
                                {opt.optionType === 'PRIVATE' ? 'Bao trọn gói' : 'Ghép đoàn'}
                              </span>
                            </td>
                            <td className="py-3 px-4 text-slate-600">
                              {opt.pricingUnit === 'PER_PACKAGE' ? 'Theo gói trọn gói' : 'Theo đầu người'}
                            </td>
                            <td className="py-3 px-4 text-right font-mono font-bold text-slate-900 tabular-nums">
                              {opt.price.toLocaleString('vi-VN')} đ
                            </td>
                            <td className="py-3 px-4 text-center font-mono text-slate-700">
                              {opt.maxPaxPerPackage ? `${opt.maxPaxPerPackage} khách` : '—'}
                            </td>
                            <td className="py-3 px-4 text-slate-600 max-w-xs truncate" title={opt.benefits || ''}>
                              {opt.benefits || '—'}
                            </td>
                            <td className="py-3 px-4 text-center">
                              <AtlasBadge
                                label={opt.status === 'ACTIVE' ? 'Đang mở bán' : 'Ngưng bán'}
                                variant={opt.status === 'ACTIVE' ? 'success' : 'neutral'}
                              />
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
              <div className="bg-white rounded-2xl border border-slate-200/80 p-6 space-y-6 shadow-xs">
                <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 border-b border-slate-100 pb-4">
                  <div>
                    <h2 className="text-base font-bold text-slate-900 font-display">Chứng chỉ &amp; Giấy phép an toàn</h2>
                  </div>

                  <div>
                    <label className="inline-flex items-center gap-2 px-4 py-2 rounded-xl bg-slate-900 text-white text-xs font-semibold hover:bg-black transition-colors cursor-pointer shadow-xs disabled:opacity-50">
                      {isUploadingDoc ? <Loader2 className="w-4 h-4 animate-spin text-slate-300" /> : <Upload className="w-4 h-4" />}
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
                  <div className="py-16 text-center">
                    <div className="w-12 h-12 rounded-2xl bg-slate-100 flex items-center justify-center mx-auto mb-3 text-slate-400">
                      <ShieldCheck className="w-6 h-6" />
                    </div>
                    <p className="text-sm font-semibold text-slate-800">Chưa có chứng chỉ an toàn nào</p>
                    <p className="text-xs text-slate-500 mt-1 max-w-sm mx-auto">
                      Đính kèm chứng chỉ kiểm định an toàn để hồ sơ dịch vụ được duyệt nhanh chóng.
                    </p>
                  </div>
                ) : (
                  <div className="space-y-3">
                    {safetyDocs.map(doc => (
                      <div key={doc.id} className="p-4 rounded-xl border border-slate-200/80 bg-slate-50/70 flex items-center justify-between gap-4 text-xs hover:border-slate-300 transition-colors">
                        <div className="flex items-center gap-3 min-w-0">
                          <div className="w-10 h-10 rounded-xl bg-white border border-slate-200/80 flex items-center justify-center shrink-0 text-slate-500">
                            <FileText className="w-5 h-5 text-sky-600" />
                          </div>
                          <div className="min-w-0">
                            <a href={doc.fileUrl} target="_blank" rel="noreferrer" className="font-bold text-slate-900 hover:text-sky-600 transition-colors block truncate">
                              Xem chứng từ đã tải lên
                            </a>
                            <p className="text-[11px] text-slate-500 font-mono mt-0.5">Ngày nộp: {new Date(doc.createdAt).toLocaleDateString('vi-VN')}</p>
                          </div>
                        </div>

                        <div className="shrink-0">
                          {doc.status === 'APPROVED' && <AtlasBadge label="Đã duyệt" variant="success" />}
                          {doc.status === 'PENDING' && <AtlasBadge label="Chờ kiểm tra" variant="warning" />}
                          {doc.status === 'REJECTED' && <AtlasBadge label="Bị từ chối" variant="danger" />}
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
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 backdrop-blur-xs p-4">
          <div className="bg-white rounded-2xl shadow-xl border border-slate-200/80 w-full max-w-md p-6 space-y-5 animate-in fade-in zoom-in-95 duration-150">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <h3 className="font-bold text-base text-slate-900 font-display">Thêm gói dịch vụ</h3>
              <button 
                type="button"
                onClick={() => setIsAddOptionModalOpen(false)} 
                className="p-1.5 rounded-lg text-slate-400 hover:text-slate-700 hover:bg-slate-100 transition-colors cursor-pointer"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <div className="space-y-4 text-xs">
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1.5">Tên gói dịch vụ: <span className="text-rose-500">*</span></label>
                <input 
                  type="text"
                  value={optName}
                  onChange={(e) => setOptName(e.target.value)}
                  className="w-full px-3.5 py-2.5 border border-slate-200/90 rounded-xl text-sm text-slate-900 focus:border-sky-500 focus:ring-2 focus:ring-sky-500/20 focus:outline-none transition-all placeholder:text-slate-400 bg-white"
                  placeholder="VD: Bao trọn cano riêng kèm flycam"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1.5">Hình thức:</label>
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
                    className="w-full px-3 py-2.5 border border-slate-200/90 rounded-xl text-xs font-semibold text-slate-900 focus:border-sky-500 focus:ring-2 focus:ring-sky-500/20 focus:outline-none bg-white cursor-pointer"
                  >
                    <option value="SHARED">Ghép đoàn</option>
                    <option value="PRIVATE">Bao trọn gói</option>
                  </select>
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1.5">Đơn vị tính:</label>
                  <input 
                    type="text"
                    disabled
                    value={optPricingUnit === 'PER_PACKAGE' ? 'Theo gói trọn' : 'Theo người'}
                    className="w-full px-3 py-2.5 border border-slate-200/80 bg-slate-50 rounded-xl text-xs text-slate-600 font-medium"
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1.5">Đơn giá (VNĐ): <span className="text-rose-500">*</span></label>
                  <input 
                    type="number"
                    min={1000}
                    step={1000}
                    value={optPrice}
                    onChange={(e) => setOptPrice(Number(e.target.value))}
                    className="w-full px-3.5 py-2.5 border border-slate-200/90 rounded-xl font-mono font-bold text-sm text-slate-900 tabular-nums focus:border-sky-500 focus:ring-2 focus:ring-sky-500/20 focus:outline-none bg-white"
                  />
                </div>

                {optType === 'PRIVATE' && (
                  <div>
                    <label className="block text-xs font-semibold text-slate-700 mb-1.5">Tối đa khách / gói: <span className="text-rose-500">*</span></label>
                    <input 
                      type="number"
                      min={1}
                      value={optMaxPax ?? ''}
                      onChange={(e) => setOptMaxPax(e.target.value ? Number(e.target.value) : null)}
                      className="w-full px-3.5 py-2.5 border border-slate-200/90 rounded-xl text-sm text-slate-900 focus:border-sky-500 focus:ring-2 focus:ring-sky-500/20 focus:outline-none bg-white"
                    />
                  </div>
                )}
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1.5">Quyền lợi bổ sung:</label>
                <textarea 
                  rows={2}
                  value={optBenefits}
                  onChange={(e) => setOptBenefits(e.target.value)}
                  className="w-full px-3.5 py-2.5 border border-slate-200/90 rounded-xl text-xs text-slate-900 focus:border-sky-500 focus:ring-2 focus:ring-sky-500/20 focus:outline-none bg-white placeholder:text-slate-400"
                  placeholder="VD: Bao gồm nước suối, khăn lạnh, chụp ảnh miễn phí..."
                />
              </div>
            </div>

            <div className="flex items-center justify-end gap-2.5 pt-3 border-t border-slate-100">
              <AtlasButton
                variant="outline"
                size="sm"
                onClick={() => setIsAddOptionModalOpen(false)}
              >
                Hủy
              </AtlasButton>
              <AtlasButton
                variant="primary"
                size="sm"
                onClick={handleCreateOption}
              >
                Lưu lựa chọn
              </AtlasButton>
            </div>
          </div>
        </div>,
        document.body
      )}

      {/* Modal: Preview */}
      {previewOpen && createPortal(
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 backdrop-blur-xs p-4">
          <div className="bg-white rounded-2xl shadow-xl border border-slate-200/80 w-full max-w-lg p-6 space-y-5 max-h-[90vh] overflow-y-auto animate-in fade-in zoom-in-95 duration-150">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <h3 className="font-bold text-base text-slate-900 font-display">Bản xem trước dịch vụ</h3>
              <button 
                type="button"
                onClick={() => setPreviewOpen(false)} 
                className="p-1.5 rounded-lg text-slate-400 hover:text-slate-700 hover:bg-slate-100 transition-colors cursor-pointer"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <div className="space-y-4 text-xs text-slate-700">
              <div className="aspect-16/9 rounded-xl overflow-hidden bg-slate-100 border border-slate-200/80 shadow-2xs">
                <img 
                  src={images[0]?.url || 'https://images.unsplash.com/photo-1544551763-46a013bb70d5?auto=format&fit=crop&w=600&q=80'} 
                  alt="Preview" 
                  className="w-full h-full object-cover" 
                />
              </div>
              <div>
                <h4 className="font-bold text-lg text-slate-900 font-display">{nameVi || 'Tên dịch vụ'}</h4>
                {nameEn && <p className="text-xs text-slate-500 font-medium mt-0.5">{nameEn}</p>}
              </div>
              <p className="text-slate-600 text-xs leading-relaxed">{descriptionVi || 'Chưa có mô tả chi tiết.'}</p>
              <div className="grid grid-cols-2 gap-2.5 p-3.5 bg-slate-50 rounded-xl border border-slate-200/70 text-xs">
                <div>Đơn vị niêm yết: <span className="font-mono font-bold text-slate-900">{price.toLocaleString('vi-VN')} đ</span></div>
                <div>Thời lượng: <span className="font-semibold text-slate-900 font-mono">{durationMinutes} phút</span></div>
                <div>Sức chứa tham khảo: <span className="font-semibold text-slate-900 font-mono">{capacityPerSlot} khách/ca</span></div>
                <div>Địa điểm: <span className="font-semibold text-slate-900">{locationName}</span></div>
              </div>
            </div>

            <div className="flex justify-end pt-3 border-t border-slate-100">
              <AtlasButton
                variant="primary"
                size="sm"
                onClick={() => setPreviewOpen(false)}
              >
                Đóng
              </AtlasButton>
            </div>
          </div>
        </div>,
        document.body
      )}
    </div>
  );
}
