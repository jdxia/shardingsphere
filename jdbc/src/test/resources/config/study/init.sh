CREATE TABLE `t_dict` (
  `id` bigint NOT NULL,
  `dict_type` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `t_order` (
  `id` bigint NOT NULL,
  `order_no` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `user_id` bigint DEFAULT NULL,
  `amount` decimal(10,2) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `t_order_item` (
  `id` bigint NOT NULL,
  `order_no` varchar(30) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `user_id` bigint DEFAULT NULL,
  `price` decimal(10,2) DEFAULT NULL,
  `count` int DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

