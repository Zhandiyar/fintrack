INSERT INTO transaction_category (name_ru, name_en, type, icon, color) VALUES
    -- системные категории расходов (EXPENSE)
-- Повседневные расходы
('Продукты', 'Groceries', 'EXPENSE', 'shopping_cart', '#FF5722'),
('Кафе и рестораны', 'Cafes and Restaurants', 'EXPENSE', 'restaurant', '#FF7043'),
('Покупки', 'Shopping', 'EXPENSE', 'shopping_bag', '#5D4037'),
('Одежда', 'Clothing', 'EXPENSE', 'checkroom', '#90A4AE'),
('Электроника', 'Electronics', 'EXPENSE', 'devices', '#78909C'),

-- Дом, жилье, быт
('Жилье', 'Housing', 'EXPENSE', 'home', '#8D6E63'),
('Коммунальные услуги', 'Utilities', 'EXPENSE', 'power', '#0097A7'),
('Связь', 'Phone and Internet', 'EXPENSE', 'phone', '#03A9F4'),
('Подписки', 'Subscriptions', 'EXPENSE', 'subscriptions', '#9575CD'),

-- Транспорт
('Транспорт', 'Transport', 'EXPENSE', 'directions_car', '#4DB6AC'),
('Такси', 'Taxi', 'EXPENSE', 'local_taxi', '#FFCA28'),
('Автообслуживание', 'Car Maintenance', 'EXPENSE', 'build', '#607D8B'),

-- Образование
('Образование', 'Education', 'EXPENSE', 'school', '#7986CB'),
('Книги и курсы', 'Books and Courses', 'EXPENSE', 'menu_book', '#5C6BC0'),

-- Здоровье
('Здоровье', 'Healthcare', 'EXPENSE', 'local_hospital', '#66BB6A'),
('Медикаменты', 'Medicine', 'EXPENSE', 'medication', '#81C784'),

-- Отдых, досуг
('Развлечения', 'Entertainment', 'EXPENSE', 'movie', '#F06292'),
('Спорт', 'Sports', 'EXPENSE', 'fitness_center', '#FF8A65'),
('Путешествия', 'Travel', 'EXPENSE', 'flight', '#4FC3F7'),
('Хобби', 'Hobbies', 'EXPENSE', 'brush', '#CE93D8'),

-- Животные
('Питомцы', 'Pets', 'EXPENSE', 'pets', '#AED581'),

-- Финансы
('Страхование', 'Insurance', 'EXPENSE', 'security', '#A1887F'),
('Долги и кредиты', 'Loans and Debts', 'EXPENSE', 'account_balance', '#B0BEC5'),

-- Подарки и помощь
('Подарки другим', 'Gifts to Others', 'EXPENSE', 'card_giftcard', '#FFB74D'),
('Благотворительность', 'Charity', 'EXPENSE', 'volunteer_activism', '#81C784'),

-- Прочее
('Другое', 'Other', 'EXPENSE', 'category', '#BDBDBD'),

    -- системные категории доходов (INCOME)
-- Основной доход
('Зарплата', 'Salary', 'INCOME', 'payments', '#4CAF50'),
('Бизнес', 'Business', 'INCOME', 'business_center', '#2E7D32'),

-- Дополнительный доход
('Фриланс', 'Freelance', 'INCOME', 'computer', '#388E3C'),
('Инвестиции', 'Investments', 'INCOME', 'show_chart', '#009688'),
('Аренда', 'Rental Income', 'INCOME', 'apartment', '#7CB342'),
('Продажа вещей', 'Selling Items', 'INCOME', 'sell', '#C0CA33'),

-- Возвраты и компенсации
('Возврат налогов', 'Tax Refund', 'INCOME', 'receipt', '#43A047'),
('Кэшбэк', 'Cashback', 'INCOME', 'redeem', '#66BB6A'),
('Подарки', 'Gifts Received', 'INCOME', 'card_giftcard', '#8BC34A'),

-- Прочее
('Прочее', 'Other Income', 'INCOME', 'add_circle', '#A5D6A7');
