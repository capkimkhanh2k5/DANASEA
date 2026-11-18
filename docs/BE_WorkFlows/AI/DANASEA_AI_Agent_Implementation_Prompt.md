# Prompt triển khai tối ưu DANASEA AI cho đội Agent

Bạn là Agent điều phối một đội kỹ thuật chịu trách nhiệm triển khai, tích hợp và kiểm thử các tính năng AI của DANASEA. Hãy đọc tài liệu, xác minh hiện trạng mã nguồn, triển khai các cải tiến có căn cứ và bàn giao kết quả có thể kiểm chứng qua API cùng giao diện người dùng.

Phạm vi thực thi của nhiệm vụ này là mã nguồn và môi trường phát triển/QA cục bộ. Hoạt động phát hành lên hệ thống bên ngoài cần căn cứ vào quyền mà người dùng đã cấp trong phiên làm việc thực tế.

## 1. Đọc tài liệu và xác lập điểm xuất phát

Đọc đầy đủ hai tài liệu sau trước khi sửa mã nguồn.

1. `docs/BE_WorkFlows/AI/DANASEA_AI_Test_Cases.md` chứa 125 tình huống USER, VENDOR, ADMIN và CROSS, câu hỏi gốc cùng tiêu chí kỳ vọng. Dùng tài liệu này để lập danh sách nghiệm thu và giữ khả năng so sánh giữa các lượt chạy. [Bộ tình huống kiểm thử](DANASEA_AI_Test_Cases.md)
2. `docs/BE_WorkFlows/AI/DANASEA_AI_Root_Cause_And_Next_Steps.md` chứa quan sát QA, nguyên nhân được đối chiếu với mã nguồn, giả thuyết cần kiểm chứng, thứ tự ưu tiên và tiêu chí kiểm thử. Đọc kỹ mục 3–9 cho từng nhóm lỗi, mục 10 cho thứ tự triển khai, mục 11–12 cho kiểm chứng và các quyết định cần xử lý. [Phân tích nguyên nhân và hướng xử lý](DANASEA_AI_Root_Cause_And_Next_Steps.md)

Đọc thêm kiến trúc và luồng AI để tìm đúng module, sau đó đối chiếu các mô tả với implementation hiện tại. [AI Workflows](WorkFlowsAI.md)

Tài liệu phân tích được viết dựa trên commit `929fdec0`, nhánh `code/danasea-ai-grounding`, ngày 18/11/2026. Khi bắt đầu, ghi nhận HEAD, branch, trạng thái working tree và các hướng dẫn `AGENTS.md` có hiệu lực. Kiểm tra những thay đổi mới hơn trước khi áp dụng nhận định lịch sử. Tiếp tục từ bản sửa đã có, đồng thời giữ các chỉnh sửa đang tồn tại của người khác.

Mốc QA cần dùng để đối chiếu là 58/125 ca đạt, tổng điểm 264/375. USER đạt 18/42, VENDOR đạt 18/35, ADMIN đạt 13/34, CROSS đạt 9/14. Lượt trước đó đạt 35/125 và 200/375. Còn 67 ca chưa đạt theo cách chấm đang sử dụng. Kết quả này mô tả môi trường QA tại thời điểm đo.

Nếu còn truy cập được máy làm việc của lượt QA trước, đọc thêm báo cáo và gói bằng chứng sau để lấy phản hồi gốc, payload thực và kết quả A/B Quyet. Đây là tài liệu bổ sung ngoài repo. Khi chuyển nhiệm vụ sang máy khác, ghi nhận nguồn nào còn truy cập được và tái tạo bằng chứng cần thiết trong QA.

- [Báo cáo QA 18/11/2026](/Users/capkimkhanh/.codex/visualizations/2026/11/17/01a1ea45-e579-7480-9fb1-48c51e14e5d7/danasea-ai-retest-2026-11-18/bao-cao-test-lai-danasea-ai.md)
- [Gói bằng chứng QA](/Users/capkimkhanh/.codex/visualizations/2026/11/17/01a1ea45-e579-7480-9fb1-48c51e14e5d7/danasea-ai-retest-2026-11-18/danasea-ai-retest-bang-chung.zip)

## 2. Kết quả cần đạt

Triển khai các nhóm cải tiến dưới đây qua đường vào mà người dùng sử dụng, gồm chat, API nghiệp vụ và thành phần giao diện liên quan. Tái sử dụng các use case, port, adapter và quy tắc nghiệp vụ hiện có. Khi một phương án trong tài liệu phân tích không còn phù hợp với mã nguồn hiện tại, chọn phương án đáp ứng cùng mục tiêu và ghi lý do trong báo cáo.

Hướng tới ngưỡng nghiệm thu đề xuất 113/125 ca và không có lỗi nghiêm trọng về phân quyền, giao dịch, dữ liệu riêng tư hoặc an toàn thời tiết. Giữ điểm chấm phản ánh kết quả quan sát được. Với các ca còn thiếu dữ liệu hoặc chưa đạt, tiếp tục xử lý trong phạm vi nhiệm vụ và ghi rõ phần việc còn lại khi bàn giao.

Các cải tiến đã có cần được kiểm tra hồi quy, đặc biệt chính sách hoàn hủy theo phiên bản đơn, hạn khiếu nại, ngân sách nhóm, quyền sở hữu, phản hồi có thẻ nguồn và các báo cáo đã sửa. Thời tiết thiếu hoặc hết hạn tiếp tục đi qua quyết định an toàn của backend. Văn bản trong listing, review và dữ liệu truy xuất được xử lý theo hợp đồng dữ liệu của hệ thống.

## 3. Tổ chức đội Agent và quyền sở hữu thay đổi

Nếu công cụ hỗ trợ, sử dụng một Agent điều phối và tối đa ba Agent chuyên trách chạy đồng thời. Điều chỉnh số lượng theo giới hạn thực tế. Agent điều phối duy trì danh sách công việc, quản lý phụ thuộc, tích hợp, giải quyết xung đột và chịu trách nhiệm kết quả cuối.

- Agent A phụ trách Copilot theo vai trò, thanh toán, kiểm duyệt và nguồn dữ liệu vận hành. Phạm vi chính gồm `RoleCopilotUseCase`, `CopilotRequest`, các công cụ Vendor/Admin và bài kiểm thử tương ứng. Ưu tiên U09, V18, V25, A10, A31, A34. Với phần đăng ký công cụ USER, gửi hợp đồng đề xuất cho Agent điều phối tích hợp.
- Agent B phụ trách planner, vòng đời lịch trình và công cụ đọc lịch. Phạm vi chính gồm `PlanItineraryUseCase`, `CustomerFeatureTool`, `CustomerFeatureToolDefinitions`, các controller/port lịch trình và bài kiểm thử liên quan. Ưu tiên U23–U25, U29–U31.
- Agent C phụ trách Quyet, taxonomy, rubric, fixture đánh giá và kiểm thử Python/adapter liên quan. Phạm vi chính là `ai-decision` và các hợp đồng backend trực tiếp phụ thuộc đầu ra worker. Ưu tiên X03, X04 và nhóm đối chứng moderation.
- Agent điều phối phụ trách `ChatUseCase`, `ConfirmBookingUseCase`, lớp tích hợp dùng chung, giao diện chat/thẻ xác nhận và U35. Điều phối các thay đổi nguồn providers, ghi nhớ lịch trình và dừng vòng công cụ sau khi thống nhất hợp đồng với các Agent chuyên trách.

Khi giao việc, ghi rõ file/module được sở hữu, đầu vào, đầu ra và tiêu chí kiểm chứng. Thông báo rằng các Agent đang làm chung codebase và phải phối hợp với thay đổi của nhau. Mỗi file dùng chung có một người tích hợp tại một thời điểm. Thay đổi ngoài phạm vi được giao cần được điều phối trước khi sửa để tránh ghi đè và xung đột.

Mỗi Agent trả về kết luận về nguyên nhân, file đã sửa, hành vi sau sửa, lệnh kiểm thử với kết quả thực, phụ thuộc còn thiếu và phần cần tích hợp. Agent điều phối kiểm chứng các kết quả qua mã nguồn và lượt chạy phù hợp trước khi đưa vào báo cáo chung.

## 4. Các nhóm triển khai cụ thể

### 4.1. Định tuyến và dữ liệu có căn cứ

Triển khai nhóm này trước để xử lý các xung đột đã xác định.

- **V25** khớp nhánh cố vấn slot do “cá nhân” được chuẩn hóa thành “ca nhan”. Thu hẹp điều kiện về ca/slot theo ngữ cảnh vận hành, nhận diện yêu cầu kiểm duyệt và nối `AssessTextUseCase` vào đúng vai trò. Trả lý do và trạng thái cần thẩm định đối với liên hệ hoặc thanh toán ngoài nền tảng.
- **A31** có cụm “link đặt tour bên ngoài” và bị hiểu thành muốn đặt tour cá nhân. Xử lý ý định kiểm duyệt bài đăng và đối tượng được đính kèm trước khi đưa ra hướng dẫn đặt dịch vụ.
- **V18** hỏi nguyên nhân khách phàn nàn nhưng nhánh trả lời chung tự suy đoán. Truy xuất review trong phạm vi vendor/service, trả số mẫu và trích dẫn có ID. Gắn nhãn các giả thuyết cần xác minh, diễn giải giới hạn khi mẫu thiếu.
- **U09** cần đọc phương thức thanh toán đang khả dụng. Dùng nguồn `PaymentProviderAvailability` và đối chiếu `GET /api/payments/providers` trên cùng môi trường. Kết quả phải phản ánh cấu hình bật/tắt thực tế của provider.

Tham chiếu nguyên nhân và tiêu chí ở mục 6–8. [Phân tích định tuyến, thanh toán và review](DANASEA_AI_Root_Cause_And_Next_Steps.md)

### 4.2. Ngưỡng cảnh báo, căn cứ rủi ro và đổi giá

- **A10** hỏi vendor hủy 45% nhưng nhận số liệu hủy toàn sàn. Phân biệt câu hỏi giải thích ngưỡng với yêu cầu đọc số liệu vendor thực. Tái sử dụng rule severity hiện hành, kiểm tra cả biên 40% và trường hợp trên 40%, giữ đúng vendor và kỳ báo cáo.
- **A34** cần căn cứ đánh giá rủi ro theo order/case. Bổ sung khả năng đọc assessment có sẵn, gồm observed facts, signals, rule version và giới hạn kết luận. Thể hiện rõ thao tác yêu cầu đánh giá mới khi use case sẽ tạo case mới.
- **U35** cần giải thích đúng quy trình khi giá đổi và nối tiếp trạng thái xác nhận. Dùng hợp đồng hiện có của `ConfirmBookingUseCase`, bao gồm lý do `PRICE_CHANGED`, thẻ thay thế và bước xác nhận giá mới. Hoàn thành cách hiển thị trạng thái này trên giao diện liên quan.

Kiểm tra thêm hết chỗ, thay đổi giá đồng thời với slot, thẻ hết hạn, nhấn xác nhận lặp và tổng tiền giữ chỗ. Tham chiếu mục 5 và 7. [Phân tích đổi giá và nghiệp vụ Admin](DANASEA_AI_Root_Cause_And_Next_Steps.md)

### 4.3. Lập lịch mới và phản hồi khi thiếu dữ liệu

- **U23** cần lịch 3 người, 08–17 giờ, ngân sách 2 triệu. Kiểm tra bằng fixture có tổ hợp slot khả thi và thời tiết hợp lệ. Đối chiếu riêng với fixture có `WEATHER_UNKNOWN`.
- **U24** cần sắp hoạt động tại Mân Thái và Sơn Trà, có khoảng đệm và thời gian di chuyển. Hiển thị rõ khi ràng buộc làm lịch bất khả thi.
- **U25** cần giữ tối đa 4 hoạt động và điều kiện không lặp dịch vụ xuyên suốt bước hỏi thêm và tạo preview.
- Chuyển `status`, `unresolvedConstraints`, hoạt động và trường còn thiếu thành phản hồi có thể hành động. Dừng vòng gọi công cụ khi đã có kết quả đủ để trả lời yêu cầu hiện tại.
- Chuẩn hóa arguments cho việc tái sử dụng kết quả. Xác minh điều kiện dừng với truy vấn tương đương và truy vấn thực sự bổ sung dữ liệu. Giữ nguyên các ràng buộc ngân sách mới nhất mà người dùng cung cấp.
- Đo lý do loại lựa chọn, số vòng model, thời gian gọi công cụ và nguyên nhân kết thúc. Dùng số đo để xác định nút thắt trước khi chỉnh giới hạn thời gian hoặc số vòng.

Tham chiếu mục 3, đặc biệt sự khác nhau giữa dữ liệu không khả dụng và lỗi xử lý yêu cầu. [Phân tích planner và vòng gọi công cụ](DANASEA_AI_Root_Cause_And_Next_Steps.md)

### 4.4. Lịch trình đã lưu và hội thoại nhiều bước

- Bổ sung công cụ đọc danh sách/chi tiết lịch, revisions và proposals qua use case hiện có, kèm owner scope.
- Ánh xạ lựa chọn người dùng đã thấy, như “lịch trình số 1”, sang UUID thực trong ngữ cảnh hội thoại. Khi lựa chọn nhập nhằng, hỏi thêm hoặc hiển thị danh sách có thể chọn.
- Đọc version hiện tại trước replan. Xử lý xung đột optimistic locking và dữ liệu thay đổi giữa lúc đọc và lúc tạo proposal.
- **U29** đọc lịch và slot bị hủy, tạo proposal thay thế, trình bày phần thay đổi và bước duyệt.
- **U30** nhận diện đúng hoạt động thứ hai, đề xuất chuyển sang chiều theo slot hiện tại, giữ các ràng buộc liên quan.
- **U31** đọc và trình bày revisions/proposals. Với nhu cầu khôi phục, đánh giá khả năng tạo proposal mới từ revision và kiểm tra lại giá, tồn kho, thời tiết theo hợp đồng hiện có.
- Tích hợp thành phần giao diện cần thiết để người dùng đọc phương án và tiếp tục hành động. Chốt cách chọn lịch trong UI từ cấu trúc sẵn có và ghi quyết định vào bàn giao.

Tham chiếu mục 4 và các quyết định ở mục 12. [Phân tích vòng đời lịch trình](DANASEA_AI_Root_Cause_And_Next_Steps.md)

### 4.5. Hiệu chỉnh Quyet theo bộ đánh giá thống nhất

- Tái hiện X03, câu phủ định ý định đặt chỗ, và X04, câu tìm tour không dấu. Đo cả worker độc lập và luồng chat.
- Thống nhất taxonomy giữa fixture và rubric phía server. Fixture có nhãn `UNCLEAR`, trong khi rubric server hiện hành dùng `OTHER` và `NEARBY`. Ghi rõ quy tắc ánh xạ hoặc thay đổi hợp đồng và tác động tới adapter/caller.
- Giữ kết quả gốc của lần A/B trước để truy vết. Lần đó có 29 đầu vào và 37 nhãn kỳ vọng, rubric hiện hành đúng 21/37 nhãn, bản thử đúng 22/37. Tính theo đầu vào đúng toàn bộ nhãn là 14/29 và 15/29.
- Xây tập dùng hiệu chỉnh và tập giữ lại để kiểm tra, bao gồm phủ định, không dấu, nhập nhằng, vị trí, support, itinerary, moderation và dữ liệu có chỉ dẫn giả. Báo cáo kết quả sau khi thống nhất taxonomy cùng cách chấm.
- Thử thay đổi rubric từng phần, đo hồi quy theo từng intent và từng nhãn moderation. Dùng lại các câu SEARCH/ITINERARY/SUPPORT từng bị bản thử làm sai.
- Đánh giá độ tin cậy bằng dữ liệu có nhãn trước khi chọn ngưỡng hỏi thêm. Khi thử model khác hoặc tinh chỉnh, so sánh trên cùng tập giữ lại cùng chi phí và độ trễ đo được.
- Kiểm tra các ca cảnh báo chống thanh toán ngoài sàn, lời mời thanh toán ngoài sàn và câu trộn có liên hệ. Xác minh hành vi khi worker unavailable và các kiểm tra ý định/quyền thao tác ở backend.

Tham chiếu đầy đủ mục 9. [Phân tích Quyet và sai khác taxonomy](DANASEA_AI_Root_Cause_And_Next_Steps.md)

## 5. Quy trình triển khai và tích hợp

1. Xác minh trạng thái repo và khởi tạo môi trường QA phù hợp từ cấu hình hiện có. Ghi nhận phiên bản model, rubric, cơ sở dữ liệu và thời gian hệ thống.
2. Lập danh sách 67 ca chưa đạt từ báo cáo trước khi có thể truy cập, hoặc dựng lại kết quả hiện tại bằng bộ 125 ca. Gắn từng nhóm việc với ca nghiệm thu và file phụ trách.
3. Tái hiện tối thiểu lỗi đang xử lý, chọn giải pháp theo hợp đồng hiện có rồi triển khai. Thêm kiểm thử hồi quy khi cần chứng minh lỗi cụ thể, ưu tiên mở rộng bộ kiểm thử sẵn có.
4. Triển khai theo thứ tự các mục 4.1, 4.2, 4.3–4.4. Phần đánh giá Quyet có thể chạy song song khi đã thống nhất quyền sở hữu file và taxonomy.
5. Tích hợp các công cụ mới vào registry, định nghĩa schema, executor, response mapping, ngữ cảnh và giao diện tương ứng. Kiểm tra toàn bộ đường đi từ input người dùng đến kết quả hiển thị.
6. Chạy kiểm thử mục tiêu cho phần thay đổi, sửa lỗi phát sinh và kiểm tra hồi quy các ca đã đạt. Khi tích hợp ổn định, thực hiện các kiểm tra CI có liên quan và vòng QA 125 ca.
7. Rà soát diff, hợp đồng API, dữ liệu lịch sử và những caller đang sử dụng. Xử lý các lỗi có thể khắc phục trong phạm vi nhiệm vụ rồi cập nhật báo cáo cuối.

Giải quyết các lựa chọn triển khai có thể đảo ngược bằng phán đoán kỹ thuật và ghi lại lý do. Khi gặp thiếu cấu hình, quyền truy cập hoặc dữ liệu thực sự cần người dùng bổ sung, mô tả đúng phần thiếu cùng tác động, đồng thời tiếp tục các phần độc lập còn thực hiện được.

## 6. Kiểm thử và cách đánh giá

Đọc workflow CI cùng các cấu hình kiểm thử hiện hành trước khi chọn lệnh. Backend đang dùng Java 21 và Maven wrapper. Frontend có `lint` và `build` trong `package.json`. Dùng các bộ kiểm thử Python của worker và lệnh chạy theo cấu hình trong repo.

Các lệnh xác minh sẵn có cần xem xét theo phần thay đổi.

```bash
# Chạy trong backend
./mvnw -B clean verify

# Chạy trong frontend sau khi cài dependency theo lockfile
npm run lint
npm run build

# Chạy trong ai-decision với môi trường Python theo cấu hình dự án
python -m unittest discover -s tests -p test_service.py -v
```

Chạy các test mục tiêu trước các lệnh xác minh rộng. Với test gọi model thật hoặc smoke cần dịch vụ, dùng cấu hình opt-in và dependency đúng theo repo, ghi rõ bài nào thực sự chạy, bài nào bị skip và lý do. Tổng hợp kết quả tránh đếm trùng các bài đã nằm trong nhiều lượt chạy. [CI hiện hành](../../../.github/workflows/ci.yml)

Vòng QA phải bao gồm các lớp sau.

- API nghiệp vụ để lấy kết quả đối chứng cho providers, giá/slot, planner, version, assessment và rule báo cáo.
- Chat đúng tài khoản USER/VENDOR/ADMIN, có ca thiếu đối tượng, đối tượng đính kèm và hội thoại nhiều bước.
- Browser cho chính sách, planner/lịch sử/proposal và thẻ xác nhận khi đổi giá. Kiểm chứng mobile theo phần thay đổi ảnh hưởng tới client và môi trường sẵn có, ghi đúng phạm vi thực tế.
- Quyet độc lập cùng tích hợp backend, đánh giá riêng intent và moderation sau khi thống nhất taxonomy.
- Phân quyền chéo user/vendor, version cũ, giá đổi, tồn kho hết, yêu cầu lặp và thời tiết thiếu/hết hạn.

Chạy lại đủ 125 tình huống, giữ câu hỏi gốc và cách chấm đã dùng để so sánh. Với ca cần dữ liệu trước như lịch đã lưu hoặc đơn sở hữu, tạo tiền điều kiện có thể tái hiện và ghi rõ payload thực. Giữ ca thiếu ID để kiểm tra hỏi thêm. Biến thể bổ sung được báo cáo cùng mục đích và bằng chứng riêng.

Thu thập cho mỗi ca ID, vai trò, câu hỏi, tiền điều kiện, tool chain, arguments phù hợp để chia sẻ, trạng thái phản hồi, nguồn dữ liệu, nội dung trả lời, điểm 0–3, kết luận và lý do. Thông tin xác thực và dữ liệu riêng tư được che khi lưu bằng chứng. HTTP 200 cần được đánh giá tiếp theo nội dung và tiêu chí nghiệp vụ.

L1 đạt từ 2/3 điểm, L2–L3 cần 3/3 theo mốc so sánh đã dùng. Chỉ ghi “đạt” khi có bằng chứng đáp ứng tiêu chí. Ghi riêng lỗi nghiêm trọng và ca chưa đủ bằng chứng. Với nhóm phụ thuộc model từng không ổn định, chạy lặp có mục tiêu và báo độ biến thiên. Các ca planner có weather hợp lệ và weather thiếu được đối chiếu riêng để xác định nguyên nhân.

Đo độ trễ và tỷ lệ `PARTIAL` bằng cùng phương pháp giữa các lượt. Đưa ra số lượng mẫu, tỷ lệ đáp ứng, số vòng model và lý do dừng. Phân biệt hiệu quả giảm gọi lặp với tốc độ suy luận hoặc độ trễ provider.

## 7. Bàn giao

Đội Agent cần cung cấp mã nguồn đã tích hợp và các kết quả sau.

1. Báo cáo Markdown trong thư mục AI của `docs`, ghi commit, môi trường, nhóm lỗi đã sửa, thay đổi hành vi, các quyết định kỹ thuật và phạm vi đã xác minh.
2. Kết quả từng ca trong bộ 125 tình huống, so sánh với mốc 58/125 và 264/375, có tổng theo vai trò, ca cải thiện, ca hồi quy và ca còn thiếu bằng chứng.
3. Gói bằng chứng tái kiểm tra được, gồm phản hồi API/chat, ảnh browser cần thiết, cấu hình fixture có thể chia sẻ và kết quả Quyet trước/sau. Ưu tiên định dạng có thể xử lý tự động cho danh sách từng ca.
4. Tóm tắt các lệnh test đã chạy, kết quả, lỗi và skip thực tế, kèm hướng dẫn tái chạy đủ ngắn để người khác xác minh.
5. Danh sách các vấn đề còn lại, mức ảnh hưởng, nguyên nhân đã biết, phụ thuộc và bước xử lý tiếp. Nêu rõ kết quả so với ngưỡng 113/125 được đề xuất trong bộ đề.

Cập nhật tài liệu phân tích khi có bằng chứng mới làm thay đổi nguyên nhân hoặc phương án. Giữ lịch sử điểm xuất phát và ngày đo để người đọc phân biệt các lượt đánh giá. Giữ nguyên bộ câu hỏi gốc, bổ sung tình huống hồi quy ở bộ đối chứng phù hợp.

Bắt đầu bằng việc đọc hai tài liệu chính, xác minh repo và chia nhiệm vụ theo quyền sở hữu ở mục 3. Tiếp tục triển khai, tích hợp và kiểm thử đến khi có kết quả bàn giao cụ thể theo mục 7.
